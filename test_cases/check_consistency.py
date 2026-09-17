#!/usr/bin/env python
"""test_cases/ 一致性检查。

核对四件事（对应 docs/testing/TEST_CASE_SCHEMA.md §5）：

    1. YAML 能被正确解析（结构没有写坏）
    2. 每条 `id` 全局唯一，且符合 `<前缀>-<三位序号>` 格式
    3. 每条 `pytest` 指针指向**真实存在**的用例函数（文件 + 函数名都对）
    4. 每条 `browser` 指针指向 tests/browser_regression.mjs 里**真实存在**的断言 ID

第 3、4 条是重点：它们把"YAML 说是这样、代码其实那样"这类漂移挡住。
前端用例（状态一致性/竞态）pytest 表达不出来——后端行为是对的，错在前端没下发请求——
所以它们在 tests/browser_regression.mjs 里，用 `browser` 字段登记。

不引入额外依赖：优先用 PyYAML（若已安装），否则退化为轻量扫描。
断言文案与业务行为的一致性无法自动核对，仍需人工——见 SCHEMA §5。

用法：
    python test_cases/check_consistency.py
退出码：0 = 全部通过；1 = 存在不一致
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CASE_DIR = ROOT / "test_cases"
TESTS_DIR = ROOT / "tests"

VALID_PREFIXES = {
    "AUTH", "USER", "FOL", "POST", "LIKE", "CMT",
    "CONV", "MSG", "NOTI", "SRCH", "FILE", "SEC", "CONC", "SMK",
}
ID_RE = re.compile(r"^[A-Z]{2,4}-\d{3}$")


def collect_pytest_ids() -> set[str]:
    """向 pytest 要一份"真实存在的用例函数"清单（不含参数化后缀）。"""
    proc = subprocess.run(
        [sys.executable, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider"],
        cwd=TESTS_DIR,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0 and "collected" not in proc.stdout:
        raise SystemExit(f"无法收集 pytest 用例：\n{proc.stdout}\n{proc.stderr}")

    module = ""
    found: set[str] = set()
    for line in proc.stdout.splitlines():
        stripped = line.strip()
        m = re.match(r"^<Module (.+?)>$", stripped)
        if m:
            module = m.group(1)
            continue
        m = re.match(r"^<Function (.+?)>$", stripped)
        if m and module:
            base = m.group(1).split("[")[0]          # 去掉参数化后缀
            found.add(f"api/{module}::{base}")
    return found


class CaseFileError(Exception):
    """用例 YAML 无法被解析（结构写坏了）。

    单独定义一个异常，是为了让 main() 把它当作一条**问题**报出来（退出码 1），
    而不是让 PyYAML 的栈直接冒到用户面前——后者看起来像脚本自己坏了，
    会掩盖"其实是 YAML 写错了"这个事实。
    """


def load_entries(path: Path) -> list[dict]:
    """解析一个 YAML 文件，返回 [{id, pytest, status}, ...]。"""
    text = path.read_text(encoding="utf-8")

    try:
        import yaml  # type: ignore

        try:
            data = yaml.safe_load(text)
        except yaml.YAMLError as exc:
            first = str(exc).splitlines()[0]
            raise CaseFileError(f"{path.name}: YAML 结构非法 -> {first}") from exc

        entries: list[dict] = []

        def walk(node):
            if isinstance(node, dict):
                if "id" in node:
                    entries.append(node)
                for value in node.values():
                    walk(value)
            elif isinstance(node, list):
                for item in node:
                    walk(item)

        walk(data)
        return entries
    except ModuleNotFoundError:
        # 无 PyYAML 时退化：本目录的条目结构固定，按块扫描即可
        entries = []
        current: dict | None = None
        for line in text.splitlines():
            if re.match(r"^\s*#", line) or not line.strip():
                continue
            m = re.match(r"^\s*-\s*id:\s*(\S+)", line)
            if m:
                if current:
                    entries.append(current)
                current = {"id": m.group(1)}
                continue
            if current is None:
                continue  # meta 段等不属于任何用例
            m = re.match(r"^\s*pytest:\s*(.+?)\s*$", line)
            if m:
                current["pytest"] = m.group(1)
            m = re.match(r"^\s*browser:\s*(.+?)\s*$", line)
            if m:
                current["browser"] = m.group(1)
            m = re.match(r"^\s*status:\s*(\S+)", line)
            if m:
                current["status"] = m.group(1)
            m = re.match(r"^\s*gap_reason:\s*(\S)", line)
            if m:
                current["gap_reason"] = True  # 只要出现过就算有
        if current:
            entries.append(current)
        return entries


def collect_browser_ids() -> set[str]:
    """从 tests/browser_regression.mjs 里抽出断言 ID（形如 BUG-002.b）。

    这些用例跑在真实浏览器里，不归 pytest 收集，因此单独解析源文件。
    文件不存在时返回空集——一致性检查会因此把相关的 browser 指针报为"不存在"，
    这正是我们想要的：脚本被删了，登记就必须跟着改。
    """
    path = TESTS_DIR / "browser_regression.mjs"
    if not path.is_file():
        return set()

    text = path.read_text(encoding="utf-8")
    # 断言写法固定为 assert('BUG-002.a', '标题', ...)
    ids = re.findall(r"assert\(\s*'([A-Za-z0-9][A-Za-z0-9.\-]*)'", text)
    return {f"browser_regression.mjs::{item}" for item in ids}


def browser_pointer_exists(target: str, real_browser: set[str]) -> bool:
    """browser 指针支持"用例级"前缀匹配。

    `browser_regression.mjs::BUG-002` 可以命中 BUG-002.a / .b / .c——
    一个 Bug 通常对应多条断言，登记时不必逐条枚举；
    但前缀写错（如 BUG-020）会匹配不到任何 ID，检查照样报错。
    """
    if target in real_browser:
        return True
    prefix = target + "."
    return any(item.startswith(prefix) for item in real_browser)


def main() -> int:
    if not CASE_DIR.is_dir():
        raise SystemExit(f"找不到目录：{CASE_DIR}")

    real = collect_pytest_ids()
    real_browser = collect_browser_ids()
    print(f"pytest 实际用例函数：{len(real)} 个")
    print(f"浏览器实际断言     ：{len(real_browser)} 条\n")

    problems: list[str] = []
    seen_ids: dict[str, str] = {}
    total = 0
    implemented = 0
    gaps = 0

    for path in sorted(CASE_DIR.glob("*.yaml")):
        try:
            entries = load_entries(path)
        except CaseFileError as exc:
            # 结构写坏的文件不跳过、不静默：记成问题并继续检查其余文件
            problems.append(str(exc))
            print(f"{path.name}: 解析失败（已记入问题清单）")
            continue
        print(f"{path.name}: {len(entries)} 条")
        for entry in entries:
            total += 1
            case_id = str(entry.get("id", "")).strip()
            pointer = entry.get("pytest")
            browser = entry.get("browser")
            status = str(entry.get("status", "")).strip()

            # --- 1. ID 格式与唯一性 ---
            if not ID_RE.match(case_id):
                problems.append(f"{path.name}: ID 格式非法 -> {case_id!r}")
            elif case_id.split("-")[0] not in VALID_PREFIXES:
                problems.append(f"{path.name}: 未知前缀 -> {case_id!r}")
            if case_id in seen_ids:
                problems.append(
                    f"{path.name}: ID 重复 {case_id}（已在 {seen_ids[case_id]} 出现）"
                )
            else:
                seen_ids[case_id] = path.name

            # --- 2. status 合法 ---
            if status not in {"implemented", "gap"}:
                problems.append(f"{path.name}/{case_id}: status 非法 -> {status!r}")

            # --- 3. 指针必须真实存在（pytest 或 browser，二者取其一） ---
            browser_target = (
                "" if browser is None else str(browser).strip().strip('"').strip("'")
            )
            if browser_target in {"null", "~", "None"}:
                browser_target = ""

            if status == "implemented":
                implemented += 1
                if browser_target:
                    if not browser_pointer_exists(browser_target, real_browser):
                        problems.append(
                            f"{path.name}/{case_id}: browser 指针不存在 -> {browser_target}"
                        )
                elif not pointer or pointer in {"null", "~", "None"}:
                    problems.append(f"{path.name}/{case_id}: status=implemented 但 pytest 为空")
                else:
                    pointer = str(pointer).strip().strip('"').strip("'")
                    if pointer not in real:
                        problems.append(
                            f"{path.name}/{case_id}: pytest 指针不存在 -> {pointer}"
                        )
            elif status == "gap":
                gaps += 1
                if not entry.get("gap_reason"):
                    problems.append(f"{path.name}/{case_id}: status=gap 但缺少 gap_reason")

    print()
    print(f"合计 {total} 条：implemented {implemented} / gap {gaps}")
    print(f"唯一 ID：{len(seen_ids)} 个")
    print()

    if problems:
        print(f"发现 {len(problems)} 处不一致：")
        for item in problems:
            print(f"  - {item}")
        return 1

    print("一致性检查通过：ID 唯一、格式合法、所有 pytest 指针均指向真实用例。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
