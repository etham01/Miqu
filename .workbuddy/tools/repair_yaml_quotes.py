"""repair_yaml_quotes.py —— 回滚"过度加引号"的破坏（fix_yaml_quotes.py 第二遍的副作用）。

## 背景（必须记录，否则下次还会踩）

`.workbuddy/tools/fix_yaml_quotes.py` 的第二遍 `needs_quoting()` 规则是：
「**未加引号的纯量值若含 `": "` → 用双引号包起来**」。

这条规则本身对"普通键值行"是安全的，但它误伤了两类结构，把文件彻底弄坏：

1. **序列里的块映射首行**：`- id: AUTH-001` → `- "id: AUTH-001"`
   → 该序列项从 mapping 退化成 scalar，下一行的子键就无处安放，
   报错 `expected <block end>, but found '<block mapping start>'`。
2. **跨行流式映射的续行**：`       pytest_const: SEED_ADMIN, purpose: "...",`
   （它其实是上一行 `- {...` 的续行）被当成普通键值行加了引号。

## 修复策略

对每一行尝试「去掉 pass-2 加的外层引号」，**用整份文档能否解析作为唯一裁判**，
并且要求"错误位置向后推进"才算有效进展 —— 这样不会把本来就该带引号的行误改。

用法：
    python .workbuddy/tools/repair_yaml_quotes.py test_knowledge
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

# 候选：`缩进 + (- 或 key: ) + "整段"`，且内层不以引号/流式字符开头
RE_CAND = re.compile(r'^(\s*)(- |[A-Za-z_][\w]*: )"(.*)"$')
# 候选 2：**流式映射的续行**被误加引号，形如
#     {code: "404, error_code: USER_NOT_FOUND}]"
# 原文是上一行 `errors: [{...},` 的续行，闭括号被吞进了引号里。
RE_FLOW = re.compile(r'^(\s*\{[^:]*: )"(.*)"$')


def unquote_candidate(line: str) -> str | None:
    """若该行像是被 pass-2 加过引号，返回去引号后的版本；否则 None。"""
    m = RE_FLOW.match(line)
    if m:
        prefix, inner = m.groups()
        if ": " in inner:
            return f"{prefix}{inner}"

    m = RE_CAND.match(line)
    if not m:
        return None
    indent, prefix, inner = m.groups()
    if not inner:
        return None
    if inner[0] in "\"'[{|>*&":            # 原本就带引号/流式/块标量 → 不是 pass-2 产物
        return None
    if ": " not in inner:                  # pass-2 的触发条件之一
        return None
    return f"{indent}{prefix}{inner}"


def score(lines: list[str]) -> tuple[int, int]:
    """越大越好：(是否解析成功, 报错行号)。"""
    try:
        yaml.safe_load("\n".join(lines))
        return (1, 10 ** 9)
    except yaml.YAMLError as e:
        m = getattr(e, "problem_mark", None)
        return (0, m.line if m is not None else -1)


def repair(path: Path) -> bool:
    lines = path.read_text(encoding="utf-8").split("\n")
    best = score(lines)
    if best[0]:
        return False

    rounds = 0
    while rounds < 500:
        rounds += 1
        improved = False
        for i in range(len(lines)):
            cand = unquote_candidate(lines[i])
            if cand is None:
                continue
            trial = list(lines)
            trial[i] = cand
            s = score(trial)
            if s > best:
                lines, best = trial, s
                improved = True
                break
        if not improved:
            break

    # ⚠️ 无论最终是否完全解析成功都要落盘：
    #    每轮只接受"让错误位置向后推进"的改动，所以过程是单调向好的，
    #    半途成果有价值（否则一次卡住就把前面所有修复丢弃 —— 这个坑踩过一次）。
    if best != score(path.read_text(encoding="utf-8").split("\n")):
        path.write_text("\n".join(lines), encoding="utf-8")

    if best[0]:
        print(f"[repaired in {rounds} round(s)] {path}")
        return True
    m = best[1]
    print(f"[PARTIAL] {path}  已推进到第 {m + 1} 行仍不可解析: {lines[m][:140] if 0 <= m < len(lines) else ''}")
    return False


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path("test_knowledge")
    targets = sorted(root.rglob("*.yaml"))
    fixed = broken = 0
    for t in targets:
        if repair(t):
            fixed += 1
        else:
            try:
                yaml.safe_load(t.read_text(encoding="utf-8"))
            except yaml.YAMLError:
                broken += 1
    print(f"\n修复 {fixed} 个文件；仍不可解析 {broken} 个。")
    return 1 if broken else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
