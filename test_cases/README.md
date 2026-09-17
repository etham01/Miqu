# test_cases —— YAML 测试用例管理

> **先说清楚定位**：本目录是**测试设计与用例管理文档**，不参与执行。
> 可执行的事实来源是 `backend/src/test`（JUnit，291）与 `tests/`（pytest，250），
> 以及前端状态层的 `tests/browser_regression.mjs`（15 项断言）。

字段定义与 ID 规则见 [`../docs/testing/TEST_CASE_SCHEMA.md`](../docs/testing/TEST_CASE_SCHEMA.md)。
覆盖对照表见 [`../docs/testing/TEST_COVERAGE_MATRIX.md`](../docs/testing/TEST_COVERAGE_MATRIX.md)。

---

## 为什么要有 YAML，又不与 pytest 1:1 复制

**为什么要有**：有些东西 pytest 表达不出来——

- **规则原文与冻结日期**：`rule` / `frozen_at` 记录了"这条业务规则是什么时候、
  因为什么定下来的"。pytest 只记录"现在断言什么"，看不出它是被冻结的约定。
- **一句话讲清"验的是什么"**：给非开发同学（产品、面试官）看的设计视图。
- **缺口登记**：`status: gap` 明确写下"这条没测，原因是……"，
  比"看起来覆盖了"诚实。

**为什么不 1:1 复制**：那等于维护两份真相。改了 pytest 忘了改 YAML，
文档立刻变成谎言。所以本目录**只登记高价值用例**：

| 登记 | 不登记 |
|---|---|
| 冻结业务规则（改它要拍板） | 普通参数校验、字段长度边界 |
| 安全边界（越权 / 注入 / 上传） | 单纯的 404 / 401 重复分支 |
| 并发与竞态 | 分页参数组合 |
| 对外演示必须走通的冒烟路径 | 已由 pytest 充分覆盖的常规流程 |

`pytest` 字段是**指针**，不是副本——它指向唯一的那份真相。
**前端用例**（pytest 表达不出来，见 `security_and_concurrency.yaml` 的 `ui_race_rules`）
用 `browser` 字段指向 `tests/browser_regression.mjs` 的断言 ID。

---

## 文件

| 文件 | 内容 | 条目 |
|---|---|---|
| [`core_business_rules.yaml`](core_business_rules.yaml) | 冻结的核心业务规则：互关私聊、关注、点赞、会话 | 37 |
| [`security_and_concurrency.yaml`](security_and_concurrency.yaml) | 认证越权、注入、上传安全、并发竞态、**前端 UI 竞态** | 36 |
| [`api_smoke.yaml`](api_smoke.yaml) | 冒烟集合（`-m smoke`，43 条） | 12 组 |

合计 **100 条**（98 implemented / 2 gap），可用 `check_consistency.py` 一键核对。

---

## 怎么用

**跑对应的可执行用例**（YAML 本身不会跑）：

```bash
cd tests

# 冒烟
python -m pytest -m smoke

# 全部互关私聊专项
python -m pytest api/test_message_mutual_follow.py api/test_conversation_mutual_follow.py -v

# 安全与并发
python -m pytest api/test_file.py api/test_concurrency.py -v
```

**按 ID 定位**：想找 `MSG-003` 对应的代码，直接在本目录搜索该 ID，
拿到 `pytest` 字段后按"文件::函数名"去 `tests/` 里找；
`browser` 字段则去 `tests/browser_regression.mjs` 搜断言 ID。

**校验指针真实性**：

```bash
python test_cases/check_consistency.py     # 退出码 0 才算通过
```

---

## 维护约定

1. **冻结规则变更时必改本目录**，并更新 `frozen_at`。
   YAML 没改说明规则变更没走完流程。
2. **用例实现后把 `status: gap` 改成 `implemented`**，填上 `pytest`，清空 `gap_reason`。
3. **只调参数边界值不用改 YAML**——那是 pytest 的职责。
4. **不要为了"用例数好看"往里加条目**。数量不是目标；
   本目录的价值在于"看一眼就知道哪些规则是冻结的、哪些还没测"。
5. `id` 一经分配**不再复用、不再改含义**。删除用例时保留 ID 并标 `status: gap`。

---

## 已知缺口（`status: gap` 的条目）

`grep -n "status: gap" -A 3 *.yaml`

当前登记的缺口：

| ID | 缺口 | 原因 |
|---|---|---|
| `SEC-004` | 不能禁用管理员账号 | 校验顺序中"不能操作自己"先命中，种子只有一个管理员，HTTP 层无输入可触达 |
| `CONC-007` | 数十线程同一瞬间首建会话 | 属压测范畴，本次只做到 8 线程 |

> `SEC-011`（头像 URL 不校验 `/uploads/` 前缀）原为 gap，
> 已于 2026-09-14 Bug Hunt 修复并转为 `implemented`——见 `docs/testing/bug_report.md` BUG-005。

这些**不做假覆盖**——登记出来，比装作测过更有价值。
