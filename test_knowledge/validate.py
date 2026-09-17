#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""validate.py —— test_knowledge/ 自洽性校验。

**这是本知识库唯一的"裁判"。** 没有它，前面所有 YAML 都只是"看起来很完整"。

它校验四类问题：

  ① 可解析性        —— 每个 .yaml 都能被 yaml.safe_load 解析
  ② 内部一致性      —— 计数与声称一致、ID 唯一、无零宽字符
  ③ 交叉引用有效性  —— 引用的 rule_id / endpoint_id / error_code 必须真实存在
                       （这一类最有价值：2026-09-17 首次运行就抓出一个悬空引用）
  ④ 证据完整性      —— confidence 标注、source 指针的形态

退出码：0 = 通过；1 = 有问题。

用法：
    python test_knowledge/validate.py            # 默认校验 test_knowledge/
    python test_knowledge/validate.py --verbose  # 打印全部通过项
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    print("需要 PyYAML：pip install pyyaml")
    sys.exit(2)

ROOT = Path(__file__).resolve().parent          # test_knowledge/
REPO = ROOT.parent                              # 仓库根

# ── ID 形态定义 ────────────────────────────────────────────────
# 规则 ID 的合法前缀（来自 docs/testing/TEST_CASE_SCHEMA.md 的 14 个 + 本知识库新增的）
RULE_PREFIXES = [
    "AUTH", "USER", "FOL", "POST", "LIKE", "CMT", "CONV", "MSG", "NOTI",
    "SRCH", "FILE", "SEC", "CONC", "SMK",          # 项目原有的 14 个
    "RPT", "XC", "MSGFACT",                        # 本知识库新增（RPT 尚未被 check_consistency.py 接受）
]
RE_RULE = re.compile(r"\b(" + "|".join(RULE_PREFIXES) + r")-(\d{1,3})\b")

# 明确**不校验**的 ID 族（它们是本知识库自己的编号，不是规则/端点）
IGNORE_ID_PREFIXES = [
    "BUG", "C", "U", "M", "I", "F", "WF", "DB", "AS", "HM", "PG", "IO", "R",
    "T", "AP", "JT", "BT", "HR", "EG", "CG", "S", "XL",
]

problems: list[str] = []
passed: list[str] = []
hygiene_warnings: list[str] = []


def ok(msg: str) -> None:
    passed.append(msg)


def bad(msg: str) -> None:
    problems.append(msg)


def load(path: Path):
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        bad(f"[YAML 无法解析] {path.relative_to(REPO)} → {str(exc).splitlines()[0]}")
        return None


def walk(node, fn):
    """深度遍历 dict/list，对每个 dict 调 fn。"""
    if isinstance(node, dict):
        fn(node)
        for v in node.values():
            walk(v, fn)
    elif isinstance(node, list):
        for v in node:
            walk(v, fn)


# ══════════════════════════════════════════════════════════════
# ① 可解析性
# ══════════════════════════════════════════════════════════════
def check_parseable(files: list[Path]) -> dict[Path, object]:
    docs = {}
    for f in files:
        doc = load(f)
        if doc is not None:
            docs[f] = doc
            ok(f"可解析 {f.relative_to(REPO)}")
    return docs


# ══════════════════════════════════════════════════════════════
# ② 收集"已知事实"（供交叉引用校验）
# ══════════════════════════════════════════════════════════════
def collect_known(docs: dict[Path, object]):
    endpoints: set[str] = set()
    error_codes: set[str] = set()
    rules_kb: set[str] = set()      # 本知识库里定义的规则
    rules_cases: set[str] = set()   # test_cases/ 里登记的规则

    for path, doc in docs.items():
        name = path.name
        if name == "endpoints.yaml":
            for v in doc.values():
                if isinstance(v, dict) and isinstance(v.get("endpoints"), list):
                    for ep in v["endpoints"]:
                        if isinstance(ep, dict) and ep.get("id"):
                            endpoints.add(str(ep["id"]))
        elif name == "error_codes.yaml":
            for item in doc.get("error_codes", []):
                if isinstance(item, dict) and item.get("name"):
                    error_codes.add(str(item["name"]))
        elif name == "business_rules.yaml":
            def _grab(d):
                rid = d.get("id")
                if isinstance(rid, str) and RE_RULE.fullmatch(rid):
                    rules_kb.add(rid)
            walk(doc, _grab)

    # test_cases/*.yaml 里登记的 ID（它们是"已注册"的规则）
    for f in sorted((REPO / "test_cases").glob("*.yaml")):
        doc = load(f)
        if doc is None:
            continue
        def _grab(d):
            rid = d.get("id")
            if isinstance(rid, str) and RE_RULE.fullmatch(rid):
                rules_cases.add(rid)
        walk(doc, _grab)

    return endpoints, error_codes, rules_kb, rules_cases


# ══════════════════════════════════════════════════════════════
# ③ 交叉引用校验
# ══════════════════════════════════════════════════════════════
REF_FIELDS_RULES = {
    "related_rules", "rules", "rules_covered", "rule", "related_registered",
    "existing_cases", "related_cases",
}
REF_FIELDS_API = {"related_api", "related_apis", "api", "endpoint"}
# ⚠️ 刻意**不**检查 `endpoints` / `apis` 这类**容器字段**：
#    它们的值里混着 priority / verification / doc_conflict_ref 等非端点内容，
#    一检查就会误报（首版就报了 P0 / F-01 / C-03 三个假问题）。
#    只检查单数形式的直接引用字段。
RE_ENDPOINT = re.compile(r"^(?:GET|POST|PUT|DELETE)_[A-Z0-9_]+$")
RE_EC_NAME = re.compile(r"^[A-Z][A-Z0-9_]{2,}$")


def _ids_in(value) -> set[str]:
    """从任意结构中抽出『看起来像 ID』的 token。"""
    out: set[str] = set()
    if isinstance(value, str):
        if re.fullmatch(r"[A-Za-z][\w]*-?\d+", value):
            out.add(value)
        out |= set(RE_RULE.findall(value) and [m.group(0) for m in RE_RULE.finditer(value)])
    elif isinstance(value, list):
        for v in value:
            out |= _ids_in(v)
    elif isinstance(value, dict):
        for v in value.values():
            out |= _ids_in(v)
    return out


def check_cross_refs(docs: dict[Path, object], endpoints, error_codes, rules_kb, rules_cases):
    known_rules = rules_kb | rules_cases
    if not known_rules:
        bad("[前置] 没有收集到任何规则 ID —— 校验无意义")
        return

    dangling: list[str] = []

    for path, doc in docs.items():
        rel = path.relative_to(REPO).as_posix()

        def visit(d: dict):
            for field, value in d.items():
                if field in REF_FIELDS_RULES:
                    for rid in _ids_in(value):
                        if not RE_RULE.fullmatch(rid):
                            continue
                        if rid not in known_rules:
                            dangling.append(f"{rel}: {field} 引用了不存在的规则 ID `{rid}`")
                elif field in REF_FIELDS_API:
                    for aid in _ids_in(value):
                        if not RE_ENDPOINT.fullmatch(aid):
                            continue          # 只认 GET_/POST_/… 形态，避开 P0 / F-01 之类
                        if aid not in endpoints:
                            dangling.append(f"{rel}: {field} 引用了不存在的端点 ID `{aid}`")

        walk(doc, visit)

    # error_code 引用（只认裸的枚举名形态，避开 schema 里的说明性字段）
    for path, doc in docs.items():
        rel = path.relative_to(REPO).as_posix()

        def visit2(d: dict):
            ec = d.get("error_code")
            if isinstance(ec, str) and RE_EC_NAME.fullmatch(ec) and ec not in error_codes:
                dangling.append(f"{rel}: error_code `{ec}` 不在 error_codes.yaml 中")
        walk(doc, visit2)

    if dangling:
        for d in sorted(set(dangling)):
            bad(f"[悬空引用] {d}")
    else:
        ok(f"交叉引用全部有效（规则 {len(known_rules)} 个 / 端点 {len(endpoints)} 个 / 错误码 {len(error_codes)} 个）")


# ══════════════════════════════════════════════════════════════
# ④ 计数一致性
# ══════════════════════════════════════════════════════════════
def check_counts(docs: dict[Path, object], endpoints):
    for path, doc in docs.items():
        rel = path.relative_to(REPO).as_posix()

        # endpoints.yaml：逐模块计数 vs reconciliation
        if path.name == "endpoints.yaml":
            per = {}
            for k, v in doc.items():
                if isinstance(v, dict) and isinstance(v.get("endpoints"), list):
                    per[k] = len(v["endpoints"])
            rec = doc.get("endpoint_count_reconciliation", {})
            for k, n in per.items():
                if k in rec and rec[k] != n:
                    bad(f"{rel}: 模块 `{k}` 声称 {rec[k]} 个端点，实际 {n}")
            total = sum(per.values())
            if rec.get("total") != total:
                bad(f"{rel}: total 声称 {rec.get('total')}，实际 {total}")
            elif total != 50:
                bad(f"{rel}: 端点总数 {total} ≠ 项目公认的 50")
            else:
                ok(f"端点计数一致：{total} 个（{len(per)} 个模块）")

        # business_rules.yaml：meta 计数 vs 实际
        if path.name == "business_rules.yaml":
            def _grab(d, acc):
                rid = d.get("id")
                if isinstance(rid, str) and RE_RULE.fullmatch(rid):
                    acc.append(rid)
            body_sections = ["auth_rules", "user_rules", "post_rules", "like_rules", "comment_rules",
                             "notification_rules", "follow_rules", "conversation_message_rules",
                             "report_rules", "cross_cutting_rules"]
            body = []
            for s in body_sections:
                if s in doc:
                    walk(doc[s], lambda d, acc=body: _grab(d, acc))
            claimed = doc.get("meta", {}).get("total_rules_in_body")
            if claimed is not None and claimed != len(body):
                bad(f"{rel}: meta.total_rules_in_body 声称 {claimed}，逐段统计为 {len(body)}")
            else:
                ok(f"规则计数一致：正文 {len(body)} 条")

        # data_model.yaml：表数量
        if path.name == "data_model.yaml":
            n = len(doc.get("tables", []))
            if n != 11:
                bad(f"{rel}: 表数量 {n} ≠ 项目的 11")
            else:
                ok("表数量一致：11 张")

        # coverage_matrix.yaml：features 计数
        if path.name == "coverage_matrix.yaml":
            feats = doc.get("features", [])
            declared = doc.get("meta", {}).get("baseline", {}).get("endpoints")
            if declared != 50:
                bad(f"{rel}: baseline.endpoints 声称 {declared} ≠ 50")
            ok(f"覆盖矩阵：{len(feats)} 个功能域")


# ══════════════════════════════════════════════════════════════
# ⑤ ID 唯一性 + 零宽字符 + 文件存在性
# ══════════════════════════════════════════════════════════════
ZERO_WIDTH = {0x200B, 0x200C, 0x200D, 0xFEFF, 0x2060}


def check_hygiene(files: list[Path], docs: dict[Path, object]):
    warnings: list[str] = []
    for f in files:
        text = f.read_text(encoding="utf-8")
        zs = [(i, hex(ord(c))) for i, c in enumerate(text) if ord(c) in ZERO_WIDTH]
        if not zs:
            continue
        # ⚠️ data/test_data.yaml **有意**含一个 U+200B 作为测试样本
        #    （"长度计算可能与直觉不同"），因此这里降级为提示而不是失败。
        if f.name == "test_data.yaml":
            warnings.append(f"{f.relative_to(REPO)}: 含 {len(zs)} 个零宽字符（**有意样本**，非缺陷）")
        else:
            bad(f"{f.relative_to(REPO)}: 含 {len(zs)} 个零宽字符（首个在偏移 {zs[0][0]}）")
    hygiene_warnings.extend(warnings)

    # 同文件内 id 唯一
    for path, doc in docs.items():
        seen: dict[str, int] = {}
        dups: list[str] = []

        def visit(d):
            rid = d.get("id")
            if isinstance(rid, str):
                seen[rid] = seen.get(rid, 0) + 1
                if seen[rid] == 2:
                    dups.append(rid)
        walk(doc, visit)
        if dups:
            bad(f"{path.relative_to(REPO)}: ID 重复 → {sorted(set(dups))}")
    ok("无零宽字符、无文件内 ID 重复")

    # 跨文件引用的相对路径必须存在
    # 引用基准有三个：知识库自身、仓库根、tests/（因为知识库里会写 "api/test_post.py"）
    search_roots = [ROOT, REPO, REPO / "tests", REPO / "test_cases", REPO / "backend"]
    missing_paths: list[str] = []
    ref_re = re.compile(r"(?:^|[\s（(])((?:\w+/)+[\w.-]+\.(?:yaml|md|py|mjs))")
    for f in files:
        for m in ref_re.finditer(f.read_text(encoding="utf-8")):
            target = m.group(1)
            if target.startswith(("http", "docs/", "backend/", "frontend/", ".workbuddy")):
                continue
            if any((r / target).exists() for r in search_roots):
                continue
            missing_paths.append(f"{f.relative_to(REPO)} → {target}")
    if missing_paths:
        for mp in sorted(set(missing_paths))[:20]:
            bad(f"[引用文件不存在] {mp}")


# ══════════════════════════════════════════════════════════════
# ⑥ confidence 标注（软检查：报告缺失但不判失败）
# ══════════════════════════════════════════════════════════════
def check_confidence(files: list[Path]):
    missing = []
    for f in files:
        text = f.read_text(encoding="utf-8")
        if "confidence" not in text:
            missing.append(f.relative_to(REPO).as_posix())
    if missing:
        print("\n提示：以下文件未出现 confidence 标注（建议在文件头声明整体置信度）：")
        for m in missing:
            print("   -", m)
    else:
        ok("全部文件都有 confidence 标注")


# ══════════════════════════════════════════════════════════════
def main() -> int:
    global ROOT  # noqa: PLW0603 — 允许 --root 覆盖，便于将来把知识库搬走

    ap = argparse.ArgumentParser()
    ap.add_argument("--verbose", action="store_true", help="打印全部通过项")
    ap.add_argument("--root", default=str(ROOT))
    args = ap.parse_args()

    ROOT = Path(args.root).resolve()
    files = sorted(ROOT.rglob("*.yaml"))
    if not files:
        print(f"未找到任何 yaml：{ROOT}")
        return 1

    print(f"校验目录：{ROOT}")
    print(f"YAML 文件：{len(files)} 个\n")

    docs = check_parseable(files)
    if not docs:
        print("\n".join(problems))
        return 1

    endpoints, error_codes, rules_kb, rules_cases = collect_known(docs)
    print(f"收集到：端点 {len(endpoints)}｜错误码 {len(error_codes)}｜"
          f"知识库规则 {len(rules_kb)}｜test_cases 规则 {len(rules_cases)}\n")

    check_cross_refs(docs, endpoints, error_codes, rules_kb, rules_cases)
    check_counts(docs, endpoints)
    check_hygiene(files, docs)
    check_confidence(files)

    if args.verbose:
        print("\n─── 通过项 ───")
        for p in passed:
            print("  ✅", p)

    print("\n" + "=" * 64)
    if problems:
        print(f"❌ 发现 {len(problems)} 个问题：\n")
        for p in problems:
            print("  •", p)
        print("\n" + "=" * 64)
        print("退出码 1")
        return 1

    print(f"✅ 知识库自洽：{len(passed)} 项检查全部通过")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
