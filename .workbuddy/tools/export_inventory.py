"""导出 pytest 用例清单（模块 / 函数名 / 首行 docstring / 标记），供文档与 YAML 使用。

只读，不修改任何测试代码。输出 JSON + Markdown 到 stdout。
"""

from __future__ import annotations

import ast
import json
import re
import subprocess
import sys
from pathlib import Path

TESTS_DIR = Path(r"D:\Projects\Miqu\tests")


def collected() -> list[tuple[str, str]]:
    """解析 `pytest --collect-only -q` 的树形输出。

    pytest 9 输出的是层级树（<Dir>/<Package>/<Module>/<Function>），
    不是 `file::func`，所以按 Module 行做状态机。
    """
    out = subprocess.run(
        [sys.executable, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider"],
        cwd=TESTS_DIR,
        capture_output=True,
        text=True,
    ).stdout

    module = ""
    pairs: list[tuple[str, str]] = []
    for line in out.splitlines():
        stripped = line.strip()
        m_mod = re.match(r"^<Module (.+?)>$", stripped)
        if m_mod:
            module = f"api/{m_mod.group(1)}"
            continue
        m_fn = re.match(r"^<Function (.+?)>$", stripped)
        if m_fn and module:
            pairs.append((module, m_fn.group(1)))
    return pairs


def docstrings() -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for path in sorted((TESTS_DIR / "api").glob("test_*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        funcs = {}
        for node in tree.body:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name.startswith("test_"):
                doc = ast.get_docstring(node) or ""
                funcs[node.name] = doc.strip().splitlines()[0] if doc else ""
        result[f"api/{path.name}"] = funcs
    return result


def main() -> None:
    pairs = collected()
    docs = docstrings()
    per_module: dict[str, list[dict[str, str]]] = {}
    for module, func in pairs:
        base = func.split("[")[0]
        per_module.setdefault(module, []).append(
            {"name": func, "base": base, "doc": docs.get(module, {}).get(base, "")}
        )

    payload = {
        "total": len(pairs),
        "modules": {m: {"count": len(v), "tests": v} for m, v in per_module.items()},
    }
    Path(r"D:\Projects\Miqu\.workbuddy\tools\test_inventory.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"total={len(pairs)} modules={len(per_module)}")
    for module, items in per_module.items():
        print(f"{module}: {len(items)}")


if __name__ == "__main__":
    main()
