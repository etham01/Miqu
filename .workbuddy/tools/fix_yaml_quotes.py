"""fix_yaml_quotes.py —— 修掉中文文案里"嵌套 ASCII 双引号"导致的 YAML 解析失败。

**为什么需要它**：写中文技术文档时很自然会写
    ai_impact: "不要断言"坏 Token"的 401"
这种行在 YAML 里是非法的：双引号标量内部的 `"` 必须转义，
但人眼很难发现，而且报错位置常指向**下一行**，排查成本很高。
本脚本把**块映射值**内部的 ASCII 双引号换成中文书名号「」，
这是中文技术文档里更地道的写法，也不改变语义。

只处理 `  key: "..."` 形式的**块映射**值。流式集合 `{a: "x", b: "y"}` 不处理
（那里的引号是成对且合法的）。

用法：
    python .workbuddy/tools/fix_yaml_quotes.py <file.yaml> [file2.yaml ...]
    python .workbuddy/tools/fix_yaml_quotes.py --all test_knowledge
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None


def fix_line(line: str) -> tuple[str, bool]:
    """返回 (新行, 是否改动)。

    处理两种常见形态：
        key: "值里带"裸引号"的文案"
        - "值里带"裸引号"的文案"
    流式集合 `{a: "x"}` 与块标量（| / >-）的内容不处理 ——
    前者引号本就成对合法，后者里的引号不参与解析。
    """
    stripped = line.lstrip()
    if not stripped or stripped.startswith("#"):
        return line, False
    if "{" in stripped or "[" in stripped:  # 流式集合，跳过
        return line, False

    # 拆出 "前缀" + "以双引号包裹的值"
    if stripped.startswith("- "):
        prefix, tail = "- ", stripped[2:].strip()
    elif ":" in stripped:
        head, _, rest = stripped.partition(":")
        prefix, tail = f"{head}: ", rest.strip()
    else:
        return line, False

    if not (tail.startswith('"') and tail.endswith('"') and len(tail) > 2):
        return line, False
    if tail.count('"') < 3:
        return line, False

    inner = tail[1:-1]
    # 把内部所有 ASCII 双引号按出现顺序交替替换为 「 」
    # ⚠️ 必须跳过 `\"` —— 那是**合法的 YAML 转义**，替换掉会变成非法转义 `\「`。
    #    这条是踩坑后加的：2026-09-17 首版没跳过，把 business_rules.yaml 一行弄坏了。
    out = []
    open_q = True
    prev_backslash = False
    for ch in inner:
        if ch == '"' and not prev_backslash:
            out.append("「" if open_q else "」")
            open_q = not open_q
        else:
            out.append(ch)
        prev_backslash = (ch == "\\") and not prev_backslash
    indent = line[: len(line) - len(stripped)]
    rebuilt = f'{indent}{prefix}"{ "".join(out) }"'
    return rebuilt, rebuilt != line


def needs_quoting(line: str) -> tuple[str, bool]:
    """第二类坑：**未加引号的纯量值里出现 ": "**。

    YAML 里 `key: 值里带 colon（status: gap）` 会解析失败（mapping values are not allowed here），
    因为解析器把第一个 `": "` 当成键值分隔。人写中文括号时特别容易带出这种值。
    """
    stripped = line.lstrip()
    if not stripped or stripped.startswith("#"):
        return line, False
    if stripped.startswith("- "):
        prefix, tail = "- ", stripped[2:].strip()
    elif ":" in stripped:
        head, _, rest = stripped.partition(":")
        prefix, tail = f"{head}: ", rest.strip()
    else:
        return line, False

    if not tail:
        return line, False
    if tail[0] in "\"'[{|>*&":  # 已被引号/流式/块标量处理过
        return line, False
    if " #" in tail:  # 行尾注释，同样需要引号，但先不动避免误伤
        return line, False
    if ": " not in tail:
        return line, False

    indent = line[: len(line) - len(stripped)]
    rebuilt = f"{indent}{prefix}\"{tail}\""
    return rebuilt, rebuilt != line


def process(path: Path) -> int:
    """两遍处理，但**第二遍带安全闸**。

    ⚠️ 血的教训（2026-09-17）：首版的第二遍 `needs_quoting()` 无条件地把
    「未加引号且含 `": "` 的值」包上双引号，结果误伤了
      · 序列里的块映射首行 `- id: AUTH-001` → `- "id: AUTH-001"`
      · 跨行流式映射的续行
    一次弄坏了 8 个文件。**任何"批量改写 YAML"的操作都必须有裁判。**
    所以第二遍只在「当前文件本来就不解析，且改完之后错误位置向后推进」时才落地。
    用 `repair_yaml_quotes.py` 可以回滚误伤。
    """
    original = path.read_text(encoding="utf-8")
    lines = original.split("\n")

    # 第一遍：修"嵌套双引号"。这个变换是语义安全的（只把裸引号换成「」），
    # 但仍跳过 \" 转义。
    changed = 0
    for i, line in enumerate(lines):
        new, did = fix_line(line)
        if did:
            lines[i] = new
            changed += 1

    # 第二遍：修"未加引号却含 ': ' 的值" —— 仅在必要时（文件不解析）且有好转才落地
    if yaml is not None and not _parses("\n".join(lines)):
        best = _score("\n".join(lines))
        for i in range(len(lines)):
            cand, did = needs_quoting(lines[i])
            if not did:
                continue
            trial = list(lines)
            trial[i] = cand
            s = _score("\n".join(trial))
            if s > best:
                lines, best = trial, s
                changed += 1

    if changed:
        path.write_text("\n".join(lines), encoding="utf-8")
        print(f"[fixed {changed} line(s)] {path}")
    return changed


def _parses(text: str) -> bool:
    if yaml is None:
        return True
    try:
        yaml.safe_load(text)
        return True
    except yaml.YAMLError:
        return False


def _score(text: str) -> tuple[int, int]:
    """越大越好：(是否解析成功, 报错行号)。"""
    if yaml is None:
        return (1, 10 ** 9)
    try:
        yaml.safe_load(text)
        return (1, 10 ** 9)
    except yaml.YAMLError as e:
        m = getattr(e, "problem_mark", None)
        return (0, m.line if m is not None else -1)


def fails_to_parse(path: Path) -> bool:
    if yaml is None:
        return False
    try:
        yaml.safe_load(path.read_text(encoding="utf-8"))
        return False
    except yaml.YAMLError:
        return True


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    targets: list[Path] = []
    if argv[1] == "--all":
        root = Path(argv[2])
        targets = sorted(root.rglob("*.yaml"))
    else:
        targets = [Path(a) for a in argv[1:]]

    total = 0
    for t in targets:
        if not t.exists():
            print(f"[missing] {t}")
            continue
        total += process(t)
    print(f"\n共修改 {total} 行。")

    if yaml is not None:
        bad = [t for t in targets if t.exists() and fails_to_parse(t)]
        if bad:
            print("\n⚠️ 仍有文件无法解析（需人工看）：")
            for b in bad:
                print("  -", b)
            return 1
        print("✅ 全部文件 YAML 解析通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
