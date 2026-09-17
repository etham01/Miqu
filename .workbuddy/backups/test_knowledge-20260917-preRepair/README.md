# Miqu Test Knowledge Base

> **这套目录是什么**：把 Miqu 项目里散落在源码、schema、配置、已有文档中的**测试相关事实**，
> 抽取成机器可读的结构化知识，作为 AI 测试系统生成测试场景 / 测试用例 / 自动化代码时的
> **Grounding（事实依据）与 Source of Truth（唯一真相）**。
>
> **这套目录不是什么**：不是又一份给人看的测试文档，也不是 `test_cases/` 的副本。

生成日期：2026-09-16 ｜ 知识来源：源码 + `database/schema.sql` + `database/data.sql` + `docs/api/` + `test_cases/`

---

## 0. 先读这一段：与现有测试体系的分工

Miqu **已经有一套成熟的测试体系**，本知识库不取代它，只补齐它缺失的一层。

| 目录 | 职责 | 是否可执行 | 真相级别 |
|---|---|---|---|
| `backend/src/test/`（JUnit，291 条） | 进程内规则覆盖，`@Transactional` 回滚 | ✅ 执行 | **事实来源** |
| `tests/`（pytest，250 条） | 真实 HTTP 端到端，真实提交 | ✅ 执行 | **事实来源** |
| `tests/browser_regression.mjs`（15 断言） | 前端状态一致性（真实 Chrome） | ✅ 执行 | **事实来源** |
| `test_cases/*.yaml`（100 条） | 高价值用例的**设计登记**与缺口诚实记录 | ❌ 不执行 | 设计视图 |
| `docs/testing/*.md` | 测试纪律、覆盖对照、执行报告、审计结论 | ❌ 不执行 | 说明视图 |
| `docs/api/*.md` | 接口的**人类可读**说明 | ❌ 不执行 | 说明视图（有滞后，见 §4） |
| **`test_knowledge/`（本目录）** | **AI 生成测试时的事实底座** | ❌ 不执行<br>✅ 可校验 | **结构化事实 + 指针** |

### 为什么不直接让 AI 读 `docs/api/*.md` 和源码

三个实际障碍，都已在本项目中出现过：

1. **`docs/api/` 有滞后**。例如 `docs/api/users.md` 至今仍写着"头像 URL 不校验 `/uploads/` 前缀"，
   而 `UserServiceImpl.java:101-104` 早在 2026-09-14（BUG-005）就加上了校验。
   AI 读文档会生成**断言 200、实际返回 400** 的错误用例。
2. **源码准确但分散**。一个端点的完整事实要跨 Controller → DTO（Bean Validation 注解）→
   ServiceImpl（业务校验）→ ErrorCode → GlobalExceptionHandler（唯一键映射）五个文件才能拼出来。
   AI 每次现拼，既慢又容易漏掉"校验顺序"这类跨文件事实。
3. **`test_cases/` 有意只登记高价值用例**（见其 README §1 登记标准），
   `AUTH` / `USER` / `POST` / `CMT` / `NOTI` 五个域**在 YAML 层完全没有登记**。
   AI 拿不到这些域的结构化规则，只能靠常识猜——而"猜"正是本项目纪律明令禁止的。

### 本知识库如何避免制造"第二份真相"

`test_cases/README.md` 的核心纪律是**不维护两份真相**。本知识库用三条机制遵守它：

1. **指针而非副本**：每条事实都带 `source`（文件:行号）与 `confidence`，
   指向源码，而不是复述源码。源码改了，`validate.py` 的抽查会失效并报错。
2. **不复制用例**：本目录**不登记测试用例**。已有 100 条用例的 ID 只在
   `rules/business_rules.yaml` 的 `existing_cases` 字段里被**引用**，用于双向追溯。
3. **可自动校验**：`validate.py` 校验内部交叉引用（规则引用的 ErrorCode / API 是否真实存在）、
   ID 唯一性与格式、置信度标注完整性。**退出码 0 才算知识库自洽**。

---

## 1. 目录结构与每个文件的存在理由

```
test_knowledge/
├── README.md                    # 本文：入口、分工、阅读路径
├── project_facts.md             # 项目事实模型（人类可读，AI 建立全局认知的起点）
│
├── api/                         # 「自动化测试到底调用什么」
│   ├── endpoints.yaml           # 50 个端点的完整结构化事实
│   ├── error_codes.yaml         # 错误码全集 + 框架层码 + 唯一键冲突映射
│   └── conventions.yaml         # 统一包装、序列化约定、HTTP 状态约定、免登录白名单、认证链路
│
├── data/                        # 「测试数据应该怎么生成」
│   ├── data_model.yaml          # 11 张表 + 实体映射 + 软删除 + 8 个枚举 + 全部边界值常量
│   ├── seed_data.yaml           # 种子账号、固定资源 ID、可依赖的断言基准
│   └── test_data.yaml           # 按字段的合法/非法/边界/特殊值生成规则
│
├── rules/                       # 「业务规则是什么」—— 本知识库最重要的部分
│   ├── business_rules.yaml      # 规则全集：given/when/then + 预期错误码与精确文案 + 证据 + 置信度
│   ├── validation_order.yaml    # 校验顺序：一个请求同时违反多条规则时，代码实际先抛哪个
│   └── side_effects.yaml        # 副作用：每个写操作连带改了什么（数据库层断言的依据）
│
├── permissions/
│   └── permission_matrix.yaml   # 角色 × 资源 × 操作 × 预期码，含禁用账号的特殊行为
│
├── states/
│   └── state_machines.yaml      # 真实存在的状态机 + 非法转换的预期错误
│
├── workflows/
│   └── workflows.yaml           # 端到端业务流程：前置 / 步骤 / 状态变化 / 清理
│
├── execution/                   # 「生成的用例怎么才能跑起来」
│   ├── pytest_contract.yaml     # fixture 全集、ApiClient API、幂等纪律、可复制模板
│   ├── junit_contract.yaml      # BaseControllerTest、MockMvc 模板、事务与种子依赖
│   └── browser_contract.yaml    # 前端回归的断言 ID 组织方式与适用场景
│
├── existing_tests.yaml          # 现有测试资产清单、规模、覆盖、已登记缺口、最近执行结果
├── test_policy.yaml             # AI 生成测试的策略：必需类别、边界策略、按项目实际启用的项
├── coverage_matrix.yaml         # Feature → API → Rule → Case → Automation 的覆盖关系
├── ai_grounding.yaml            # Allowed Facts / Forbidden Assumptions / Confidence 分级
├── generation_contract.yaml     # AI Test Generation Contract：输入、grounding、生成、校验、重试、执行
├── issues.yaml                  # UNKNOWN / INFERRED / CONFLICT / MISSING 全清单（不隐藏问题）
└── validate.py                  # 知识库自洽性校验脚本（退出码 0 = 通过）
```

**没有创建的空目录**：`architecture/`（本项目单体架构，一节 `project_facts.md` 足够）、
`external_dependencies/`（除 MySQL 与本地文件存储外无外部服务，事实已并入 `project_facts.md`）、
`schemas/`（JSON Schema 由 `validate.py` 内联校验，本项目规模不需要独立 schema 目录）。

---

## 2. 每个文件谁用、用什么

| 文件 | LLM（生成） | Pydantic（结构校验） | Business Validator | Coverage Validator | 自动化执行器 |
|---|---|---|---|---|---|
| `project_facts.md` | 建立全局认知 | — | — | — | — |
| `api/endpoints.yaml` | 拼请求 | 校验 method/path/参数名 | 校验参数是否存在 | 统计 API 覆盖 | 生成 `client.get(...)` |
| `api/error_codes.yaml` | 写预期错误 | — | **校验 code 与 message 是否真实存在** | — | 生成断言 |
| `api/conventions.yaml` | 判断要不要断言 HTTP 状态 | — | 校验认证要求 | — | 决定带不带 Token |
| `data/data_model.yaml` | 造数据、写库层断言 | 校验字段名 | 校验字段/枚举值是否存在 | 统计字段覆盖 | 生成 SQL 断言 |
| `data/seed_data.yaml` | 复用固定账号 | — | 校验前置数据可满足 | — | setup |
| `data/test_data.yaml` | 取边界值 | — | 校验边界值与常量一致 | 统计边界覆盖 | 生成 parametrize |
| `rules/business_rules.yaml` | **生成场景的主依据** | 校验 rule_id 引用 | **逐条比对预期结果** | **统计规则覆盖** | 生成断言 |
| `rules/validation_order.yaml` | 判断叠加违规时该断言哪个码 | — | **拦截"预期码与实际抛出顺序不符"** | — | 生成断言 |
| `rules/side_effects.yaml` | 补数据库层断言 | — | 校验副作用是否被断言 | 统计副作用覆盖 | 生成 verify |
| `permissions/permission_matrix.yaml` | 生成越权用例 | 校验角色是否存在 | 校验预期状态码 | 统计角色覆盖 | 选 fixture |
| `states/state_machines.yaml` | 生成非法转换用例 | — | 校验状态值是否存在 | 统计状态覆盖 | 生成 setup |
| `workflows/workflows.yaml` | 生成端到端场景 | — | 校验步骤依赖可满足 | — | 生成 setup/cleanup |
| `execution/pytest_contract.yaml` | **生成可运行代码** | 校验 fixture 名是否存在 | — | — | **直接决定代码能否跑通** |
| `existing_tests.yaml` | 避免重复生成 | — | — | **算增量覆盖** | 定位已有用例 |
| `test_policy.yaml` | 决定生成哪些类别 | — | — | **判定覆盖是否达标** | — |
| `coverage_matrix.yaml` | — | — | — | **主数据源** | — |
| `ai_grounding.yaml` | **约束不得臆造** | — | **Forbidden 清单即校验规则** | — | — |
| `generation_contract.yaml` | 流程契约 | 输出结构契约 | 校验开关 | 校验开关 | 可执行性门槛 |
| `issues.yaml` | **禁止把 UNKNOWN 当事实** | — | 冲突项拦截 | 缺口即待覆盖项 | — |

---

## 3. AI 使用本知识库的推荐路径

```
Requirement（要测什么）
  ↓
① 读 project_facts.md          → 建立全局认知：这是什么系统、有哪些模块
  ↓
② 读 ai_grounding.yaml         → 明确"能用什么事实、禁止假设什么"
  ↓
③ Feature Retrieval            → coverage_matrix.yaml 找到目标 feature
  ↓
④ API / Rule Retrieval         → api/endpoints.yaml + rules/business_rules.yaml
                                   取该 feature 涉及的端点与规则
  ↓
⑤ 补齐上下文                   → data/data_model.yaml（字段约束与边界值）
                                   permissions/permission_matrix.yaml（角色维度）
                                   states/state_machines.yaml（状态维度）
                                   rules/validation_order.yaml（叠加违规时的预期）
                                   rules/side_effects.yaml（数据库层断言）
  ↓
⑥ Scenario Generation          → 按 test_policy.yaml 的 required_categories 展开
  ↓
⑦ Case Generation              → 每条用例必须带 traceability（feature/api/rule）
  ↓
⑧ 代码生成                     → 严格按 execution/pytest_contract.yaml 的 fixture 与写法
  ↓
⑨ 自检                         → issues.yaml：涉及 UNKNOWN/CONFLICT 的部分不得当作事实断言
```

**硬性要求**：第 ⑤ 步不能跳过。跳过 `validation_order.yaml` 会导致
"叠加违规"类用例断言错误的 code（本项目已知的 SEC-004 缺口就是这个成因）。

---

## 4. 已知的事实冲突（必须先知道再用）

本知识库在抽取过程中发现**文档与代码不一致**。按 `docs/testing/TEST_CASE_SCHEMA.md` §5.2 的红线
（"以实测行为为准修正文档"），**一律以源码为准**。完整清单见 [`issues.yaml`](issues.yaml)，最要紧的三条：

| # | 冲突 | 文档说法 | 代码实际 | 影响 |
|---|---|---|---|---|
| C-01 | 头像 URL 前缀校验 | `docs/api/users.md` 称"不校验前缀，不要写外链 400 的断言" | `UserServiceImpl.java:101-104` **明确校验**，外链返回 400 `INVALID_IMAGE_URL` | 🔴 AI 读文档会生成**反向错误**的断言 |
| C-02 | `EMPTY_COMMENT` 错误码 | `ErrorCode` 中存在，文档列出 | **全库无任何引用**（死枚举）；空评论实际返回 `PARAM_INVALID` | 🟠 AI 会断言一个永不出现的 message |
| C-03 | `targetType=4` 的举报 | 文档称返回 `INVALID_TARGET_TYPE` | Bean Validation `@Max(3)` 先拦为 `PARAM_INVALID`，Service 分支**不可达** | 🟠 同上 |

**规则**：AI 生成用例时，凡 `issues.yaml` 标为 CONFLICT 的条目，
必须以 `code_reality`（代码实际）为断言依据，不得以 `doc_claim` 为依据。

---

## 5. 校验

```bash
# 知识库自身是否自洽（交叉引用、ID 唯一性、置信度完整性）
python test_knowledge/validate.py          # 退出码 0 = 通过

# 与已有 test_cases/ 的指针一致性（项目原有脚本，本知识库不重复其职责）
python test_cases/check_consistency.py     # 退出码 0 = 通过
```

两个脚本职责不重叠：`check_consistency.py` 校验 `test_cases/` 的用例指针是否指向真实测试函数；
`validate.py` 校验本知识库的事实引用是否自洽、是否与源码常量对齐。

---

## 6. 维护约定

1. **源码是上游，本目录是下游。** 改了 Controller / DTO / ServiceImpl / ErrorCode / BizConstants /
   schema.sql 中任何被本目录引用的事实，**必须同步本目录**，否则 `validate.py` 的抽查会失效。
2. **发现新的文档-代码冲突**，登记进 `issues.yaml`，不要就地"选一个版本"。
3. **禁止为了完整度编造事实。** 拿不准的写 `confidence: UNKNOWN` 并在 `issues.yaml` 登记——
   诚实的缺口比漂亮的假覆盖有价值（这条纪律与 `test_cases/README.md` §已知缺口一致）。
4. **新增业务规则**时，先确认它是否已在 `test_cases/*.yaml` 登记为用例；
   若已登记，在 `existing_cases` 里引用其 ID，**不要复制其内容**。
5. **本目录不登记测试用例**，只登记事实与规则。用例的家在 `test_cases/` 与 `tests/`。
