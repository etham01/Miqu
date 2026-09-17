"""一次性同步文档里的基线数字（2026-09-16 头像修复后）。

新基线：
    pytest      248 -> 250
    JUnit       291（不变）
    浏览器回归   12 -> 15
    用例登记     98 -> 100 条（implemented 96 -> 98，gap 2 不变）
    test_file.py 14 -> 16

刻意写成「逐条精确替换 + 报告命中」，避免正则误伤；
没命中的条目会打印出来，便于人工补。
"""

from __future__ import annotations

from pathlib import Path

# 本脚本位于 <repo>/.workbuddy/tools/，所以要上溯三级才是仓库根
ROOT = Path(__file__).resolve().parents[2]

REPLACEMENTS: list[tuple[str, str, str]] = [
    # ---------- 根 README ----------
    ("README.md", "pytest 接口自动化（248 个用例）", "pytest 接口自动化（250 个用例）"),
    ("README.md", "前端状态一致性回归（真实 Chrome，12 项断言）",
     "前端状态一致性回归（真实 Chrome，15 项断言）"),
    ("README.md", "cd tests && python -m pytest          # 248 个用例",
     "cd tests && python -m pytest          # 250 个用例"),
    ("README.md", "**248 个用例的分布：**", "**250 个用例的分布：**"),
    ("README.md", "| `api/test_file.py` | 14 | **上传安全：魔数、大小、MIME、空/极小文件、可访问性** |",
     "| `api/test_file.py` | 16 | **上传安全：魔数、大小、MIME、空/极小文件、可访问性**；**缺失静态资源返回真 404** |"),
    ("README.md", "真实 Chrome，12 项断言）", "真实 Chrome，15 项断言）"),
    ("README.md", "| P6 | pytest 接口自动化（248 用例）", "| P6 | pytest 接口自动化（250 用例）"),
    ("README.md", "两套测试共 **539** 个用例", "两套测试共 **541** 个用例"),
    ("README.md", "（JUnit 291 + pytest 248），另加前端浏览器回归 **12** 项断言",
     "（JUnit 291 + pytest 250），另加前端浏览器回归 **15** 项断言"),

    # ---------- tests/README.md ----------
    ("tests/README.md",
     "| `api/test_file.py` | 14 | 文件上传安全：正常上传（png/jpg/gif/webp）、**URL 可访问闭环**、空文件、超 5MB、**伪后缀（魔数不符）被拒**、**MIME 与内容不符被拒**、极小文件、真图错后缀放行、未登录 401、缺 `file` 字段 / 非 multipart → 400 |",
     "| `api/test_file.py` | 16 | 文件上传安全：正常上传（png/jpg/gif/webp）、**URL 可访问闭环**、空文件、超 5MB、**伪后缀（魔数不符）被拒**、**MIME 与内容不符被拒**、极小文件、真图错后缀放行、未登录 401、缺 `file` 字段 / 非 multipart → 400、**缺失静态资源真 404 / 业务接口仍 200** |"),
    ("tests/README.md",
     "合计 **248 个用例**（2026-09-14 实测；其中 246 为基线，新增 2 条为 BUG-005 回归）。",
     "合计 **250 个用例**（2026-09-16 实测；246 为最初基线，+2 为 BUG-005 头像前缀回归，\n+2 为 BUG-007 静态资源 404 回归）。"),
    ("tests/README.md", "12 项断言，用真实 Chrome 覆盖 **pytest 表达不出来**的那一层：",
     "15 项断言，用真实 Chrome 覆盖 **pytest 表达不出来**的那一层："),
    ("tests/README.md",
     "最近一次结果：**12/12 PASS**（2026-09-14，修复后）。修复前为 5 PASS / 6 FAIL。",
     "最近一次结果：**15/15 PASS**（2026-09-16，修复后）。\nBUG-002/003/004 修复前为 5 PASS / 6 FAIL；BUG-006 修复前为破图 2 个、占位 0 个。"),

    # ---------- docs/api/README.md ----------
    ("docs/api/README.md", "现有自动化测试：**pytest 248 个用例**（13 个测试文件），",
     "现有自动化测试：**pytest 250 个用例**（13 个测试文件），"),
    ("docs/api/README.md", "| 用例数 | 291 | 248 |", "| 用例数 | 291 | 250 |"),
    ("docs/api/README.md", "（真实 Chrome，12 项断言）", "（真实 Chrome，15 项断言）"),

    # ---------- MIQU_TEST_SYSTEM.md ----------
    ("docs/testing/MIQU_TEST_SYSTEM.md", "| 数量 | **291** | **248** |", "| 数量 | **291** | **250** |"),
    ("docs/testing/MIQU_TEST_SYSTEM.md",
     "| 当前结果 | 291 run / 0 failures | 248 passed / 0 failed |",
     "| 当前结果 | 291 run / 0 failures | 250 passed / 0 failed |"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "pytest (248)          浏览器回归 (12)",
     "pytest (250)          浏览器回归 (15)"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "# 13 个测试文件 / 248 个用例",
     "# 13 个测试文件 / 250 个用例"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "（真实 Chrome，12 项断言）", "（真实 Chrome，15 项断言）"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "| pytest 用例 | **248** |", "| pytest 用例 | **250** |"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "| 浏览器回归断言 | **12**", "| 浏览器回归断言 | **15**"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "**539**（不含浏览器层）/ **551**（含）",
     "**541**（不含浏览器层）/ **556**（含）"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "最近一次执行：**539 条全部通过，0 失败**（另加浏览器回归 12/12）。",
     "最近一次执行：**541 条全部通过，0 失败**（另加浏览器回归 15/15）。"),
    ("docs/testing/MIQU_TEST_SYSTEM.md", "已验证（2026-09-14 连续两次执行，均为 `248 passed`）。",
     "已验证（2026-09-16 实测 `250 passed`；套件幂等，中途无需重置数据库）。"),

    # ---------- TEST_COVERAGE_MATRIX.md ----------
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "| pytest 用例 | **248**（13 个文件） |",
     "| pytest 用例 | **250**（13 个文件） |"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "| 浏览器回归断言 | **12**", "| 浏览器回归断言 | **15**"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "**539**（JUnit + pytest）／ **551**（含浏览器层）",
     "**541**（JUnit + pytest）／ **556**（含浏览器层）"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "| 合计 | **50** | 13 个文件 | **248** | 21 个类 | **291** |",
     "| 合计 | **50** | 13 个文件 | **250** | 21 个类 | **291** |"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "> 12 项断言见 `tests/browser_regression.mjs`，",
     "> 15 项断言见 `tests/browser_regression.mjs`，"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md",
     "| `README` 用例数 518 / pytest 229 → 实际 539（JUnit 291 + pytest 248） | **已更正** |",
     "| `README` 用例数 518 / pytest 229 → 实际 541（JUnit 291 + pytest 250） | **已更正** |"),
    ("docs/testing/TEST_COVERAGE_MATRIX.md", "| 用户 `/api/users` | 13 | `test_user.py`、`test_follow.py`、`test_search.py` | 20 + 19 + 15 |",
     "| 用户 `/api/users` | 13 | `test_user.py`、`test_follow.py`、`test_search.py` | 20 + 19 + 15 |"),

    # ---------- TEST_EXECUTION_REPORT.md ----------
    ("docs/testing/TEST_EXECUTION_REPORT.md", "248 collected", "250 collected"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "248 passed", "250 passed"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "连续执行**两次**，两次均为 `248 passed`",
     "连续执行**两次**，两次均为 `250 passed`"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "| 合计 | **248** | |", "| 合计 | **250** | |"),
    ("docs/testing/TEST_EXECUTION_REPORT.md",
     "> 同日 Bug Hunt 修复阶段新增 `test_user.py` 2 条 BUG-005 回归（246 → 248）。",
     "> 同日 Bug Hunt 修复阶段新增 `test_user.py` 2 条 BUG-005 回归（246 → 248）；\n> 2026-09-16 头像修复阶段新增 `test_file.py` 2 条 BUG-007 回归（248 → 250）。"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "248 (pytest) + 291 (JUnit) = 539 条自动化用例",
     "250 (pytest) + 291 (JUnit) = 541 条自动化用例"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "12 项断言 / 12 PASS / 0 FAIL",
     "15 项断言 / 15 PASS / 0 FAIL"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "修复前为 **5 PASS / 6 FAIL**（这就是进入修改阶段的准入闸门），修复后 12/12。",
     "修复前为 **5 PASS / 6 FAIL**（BUG-002/003/004 的准入闸门），修复后 15/15。"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "所以它红不会污染上面 248 条的绿灯。",
     "所以它红不会污染上面 250 条的绿灯。"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "| `api/test_file.py` | **14** |", "| `api/test_file.py` | **16** |"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "Pytest:   248 collected / 248 passed / 0 failed",
     "Pytest:   250 collected / 250 passed / 0 failed"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "合计:     539 条自动化用例，0 失败",
     "合计:     541 条自动化用例，0 失败"),
    ("docs/testing/TEST_EXECUTION_REPORT.md", "浏览器:   12 项断言 / 12 PASS（前端状态一致性回归）",
     "浏览器:   15 项断言 / 15 PASS（前端状态一致性 + 图片降级回归）"),

    # ---------- test_cases/README.md ----------
    ("test_cases/README.md", "（pytest，248）", "（pytest，250）"),
    ("test_cases/README.md", "（12 项断言）", "（15 项断言）"),
    ("test_cases/README.md", "| [`security_and_concurrency.yaml`](security_and_concurrency.yaml) | 认证越权、注入、上传安全、并发竞态、**前端 UI 竞态** | 34 |",
     "| [`security_and_concurrency.yaml`](security_and_concurrency.yaml) | 认证越权、注入、上传安全、并发竞态、**前端 UI 竞态** | 36 |"),
    ("test_cases/README.md", "合计 **98 条**（96 implemented / 2 gap），可用 `check_consistency.py` 一键核对。",
     "合计 **100 条**（98 implemented / 2 gap），可用 `check_consistency.py` 一键核对。"),
    ("test_cases/README.md", "| `api_smoke.yaml`](api_smoke.yaml) | 冒烟集合", "| `api_smoke.yaml`](api_smoke.yaml) | 冒烟集合"),
]


def main() -> int:
    misses: list[str] = []
    hits = 0
    per_file: dict[str, list[tuple[str, str]]] = {}

    for rel, old, new in REPLACEMENTS:
        path = ROOT / rel
        if not path.is_file():
            misses.append(f"{rel}: 文件不存在")
            continue
        text = path.read_text(encoding="utf-8")
        if old not in text:
            misses.append(f"{rel}: 未命中 -> {old[:60]!r}")
            continue
        if old == new:
            continue
        path.write_text(text.replace(old, new), encoding="utf-8")
        hits += 1
        per_file.setdefault(rel, []).append((old[:50], new[:50]))

    print(f"已替换 {hits} 处：")
    for rel, items in sorted(per_file.items()):
        print(f"  {rel}  ({len(items)} 处)")
    if misses:
        print(f"\n未命中 {len(misses)} 处：")
        for m in misses:
            print(f"  - {m}")
        return 1
    print("\n全部命中。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
