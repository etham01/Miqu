# Miqu 测试体系总览（MIQU Test System）

本文是 Miqu 测试体系的**入口文档**：讲清楚"有几套测试、各自负责什么、
怎么跑、结果去哪看、规矩是什么"。

其它测试文档的分工：

| 文档 | 回答什么问题 |
|---|---|
| **本文** `MIQU_TEST_SYSTEM.md` | 测试体系长什么样，怎么跑，谁负责什么 |
| [`TESTING_GUIDELINES.md`](../TESTING_GUIDELINES.md) | 写用例时的规范与纪律（**动手前先读**） |
| [`TEST_CASE_SCHEMA.md`](TEST_CASE_SCHEMA.md) | YAML 用例的字段定义与 ID 规则 |
| [`TEST_COVERAGE_MATRIX.md`](TEST_COVERAGE_MATRIX.md) | 接口 / 规则 → 用例的覆盖对照表 |
| [`TEST_EXECUTION_REPORT.md`](TEST_EXECUTION_REPORT.md) | 最近一次真实执行结果 |
| [`API_DOCUMENT_AUDIT.md`](API_DOCUMENT_AUDIT.md) | 接口与文档一致性的审计结论 |
| [`PERFORMANCE_TEST_PLAN.md`](PERFORMANCE_TEST_PLAN.md) | JMeter 压测方案 |
| [`../../tests/README.md`](../../tests/README.md) | pytest 套件的使用说明与已知缺口 |

---

## 1. 两套测试，职责互补

Miqu 有意采用**双测试体系**，两套都保留，不是重复劳动。

| | JUnit + MockMvc（`backend/src/test`） | pytest（`tests/`） |
|---|---|---|
| 调用方式 | 进程内，不起 HTTP | 真实 HTTP 请求 |
| 数据库 | 真实库，`@Transactional` 自动回滚 | 真实库，**真实提交不回滚** |
| 速度 | 快 | 慢（受网络与 DB 影响） |
| 断言风格 | 可依赖种子数据的**绝对值** | 以**差值**为主，不依赖全局状态 |
| 擅长 | 规则密集的分支覆盖、异常分支 | 端到端联调、序列化格式、过滤器链、事务提交 |
| 数量 | **291** | **250** |
| 当前结果 | 291 run / 0 failures | 250 passed / 0 failed |

**为什么要两套**：MockMvc 快且能回滚，适合把业务规则钉死；
pytest 慢但真实，能发现"单元测试全绿、联调就挂"的问题
（序列化格式、CORS、过滤器顺序、事务没提交）。

两套都跑过才叫验证完成。

---

## 2. 测试分层

```
                    Miqu 测试体系
                          │
   ┌──────────────────────┼──────────────────────┬──────────────────────┐
   ↓                      ↓                      ↓                      ↓
 YAML 用例文档        JUnit (291)            pytest (250)          浏览器回归 (15)
 设计/管理            进程内规则覆盖          真实 HTTP 端到端       真实 Chrome
 (test_cases/)        + 回滚                  + 真实提交             前端状态一致性
   │                      │                      │                      │
   └──────────┬───────────┴──────────┬───────────┴──────────┬───────────┘
              ↓                      ↓                      ↓
        一致性靠脚本核对         执行结果进报告
   (test_cases/check_consistency.py) (TEST_EXECUTION_REPORT.md)
```

第四层是 2026-09-14 Bug Hunt 之后补的：有三个真实缺陷（BUG-002/003/004）
**后端每次返回都是对的**，错在前端没把请求发出去——pytest 打 HTTP 根本看不见。
它们只能靠真实浏览器回归，见 `tests/browser_regression.mjs`
与 `test_cases/security_and_concurrency.yaml` 的 `CONC-008` ~ `CONC-011`。

三类测试的**真相来源**不同，必须分清：

- **pytest 与 JUnit 是可执行的事实来源**——它们跑出来什么，系统就是什么。
- **YAML 是设计与用例管理文档**，不参与执行，也不与 pytest 做 1:1 复制
  （否则要维护两份真相）。见 `test_cases/README.md`。

---

## 3. 目录结构

```
backend/src/test/java/com/miqu/      # JUnit，21 个测试类 / 291 个 @Test
tests/
├── conftest.py                      # pytest 全局夹具（后端探活、随机用户工厂）
├── pytest.ini                       # 标记定义（smoke / read / write）
├── requirements.txt
├── utils/client.py                  # HTTP 客户端封装（业务码断言）
├── api/                             # 13 个测试文件 / 250 个用例
├── browser_regression.mjs           # 前端状态一致性回归（真实 Chrome，15 项断言）
├── browser_check.mjs                # 页面走查（控制台报错 / 未捕获异常）
├── e2e_walkthrough.py               # 端到端真实链路
└── perf/users.csv                   # JMeter 压测账号
test_cases/                          # YAML 用例文档（设计层，不参与执行）
docs/testing/                        # 本目录：测试相关文档
```

---

## 4. 怎么跑

### 4.1 JUnit

```bash
# ⚠️ 先重置数据库：JUnit 依赖种子数据的绝对值
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql

cd backend && mvn test
```

### 4.2 pytest

```bash
# 1. 启动后端（pytest 打的是真实 HTTP）
cd backend && mvn spring-boot:run

# 2. 装依赖（首次）
pip install -r tests/requirements.txt

# 3. 跑
cd tests && python -m pytest
python -m pytest -m smoke           # 只跑冒烟
python -m pytest -m read            # 只跑只读用例（不改数据）
python -m pytest --collect-only -q  # 只看收集到多少条
```

后端没启动时不会得到一堆"连接被拒绝"，而是直接给出明确提示并退出
（`conftest.py` 的 `ensure_backend_running`）。

### 4.3 端到端真实链路

```bash
python .workbuddy/tools/e2e_walkthrough.py http://127.0.0.1:8081
```

按真实用户流程逐步断言（注册 → 登录 → 搜索 → 关注 → 互关 → 私聊 →
发消息 → 读历史 → 标记已读 → 取消互关 → 发送失败 → 历史仍可读 →
重新互关 → 恢复发送 → 动态/点赞/评论/通知/上传/管理端）。

### 4.4 前端真实浏览器

```bash
cd frontend && npm run dev          # 先起前端
node tests/browser_check.mjs        # 另开一个终端
```

可选：需要本机有 Chrome + playwright（未列入 `requirements.txt`）。
它会用真实浏览器走 登录 → 首页 → 私聊 → 通知 → 搜索 → 个人中心，并收集
控制台报错与未捕获异常。最近一次结果：**11/12**（唯一"失败"是外部头像图床被网络策略拦截，非代码问题）。

---

## 5. 当前规模

| 维度 | 数量 |
|---|---|
| 后端接口 | **50**（16 个 Controller） |
| JUnit 用例 | **291** |
| pytest 用例 | **250** |
| 浏览器回归断言 | **15** |
| 自动化用例合计 | **541**（不含浏览器层）/ **556**（含） |
| pytest 覆盖的接口 | 50/50（重构后 100%） |
| YAML 登记的核心规则 | 见 `test_cases/` |

最近一次执行：**541 条全部通过，0 失败**（另加浏览器回归 15/15）。详见
[`TEST_EXECUTION_REPORT.md`](TEST_EXECUTION_REPORT.md)。

---

## 6. 三条硬纪律

写或改测试时，这三条不能破：

1. **不许把测试改绿。** 禁止删除测试、`skip`、放宽断言、
   捕获异常后忽略、改测试数据掩盖 Bug。测试红了先查代码，不要先查测试。
   （唯一例外见下方第 4 点：种子数据漂移。）
2. **pytest 断言用差值，不用绝对值。** 写入会真实落库且没有删除接口，
   库里数据会随运行次数增长。写 `assert 用户总数 == 21` 第二次跑就挂。
3. **写数据只用当次运行新建的随机用户**（`fresh_user` / `fresh_users` 夹具）。
   不要去改种子账号，否则会污染其他用例。

**第 4 点（唯一的合法"红转绿"路径）**：少量针对**种子数据**的绝对值断言
（如"test001 有 14 个粉丝"）在**手工用 curl / 前端操作过之后**会失效。
这种情况的正确处理是**重置数据库**，而不是改断言：

```bash
mysql -u root -p < database/schema.sql && mysql -u root -p < database/data.sql
```

---

## 7. 幂等性

pytest 套件是**幂等**的：连跑两次结果一致，中途不需要重置数据库。
已验证（2026-09-16 实测 `250 passed`；套件幂等，中途无需重置数据库）。

保证幂等的两个手段：

1. 断言用差值而非绝对值；
2. 需要写数据时一律用随机用户名工厂（`fresh_user` / `fresh_users`），
   不使用固定用户名——测试会真实创建用户且**没有删除用户的接口**，
   固定用户名第二次运行就会撞唯一键。

跑完一轮后库里会多出一些测试用户，这是**预期行为**，不是缺陷。

---

## 8. 测试发现过的真实缺陷（沉淀）

这套测试不是"事后补的截图工程"，它真的拦下过问题。典型例子：

| 缺陷 | 怎么被发现的 | 沉淀下来的防线 |
|---|---|---|
| `Report` 实体误继承 `@TableLogic` 基类，而 `report` 表无 `deleted` 列 → 首次查询暴雷 | 新接口第一次查这张表 | `EntitySchemaConsistencyTest` |
| Hikari 懒初始化导致"健康检查 UP 但业务请求全 500" | 启动自检 | `HealthControllerTest` + 健康接口真实探测 DB |
| 并发首次建会话返回 409（快照读看不到并发事务新提交的行） | 新增的 8 线程并发用例 | `test_concurrency.py` + `REQUIRES_NEW` 修复 |
| 上传接口收到非 multipart / 缺 `file` 时返回 500 而非 4xx | 新增的上传安全用例 | `test_file.py` + `GlobalExceptionHandler` 补映射 |
| `conftest.py` 的 `anonymous` 夹具若是 session 级，会因 `login()` 污染 Token 导致真删 post 1 | 连锁误报 | 夹具显式写成函数级（有注释说明） |

---

## 9. 已知覆盖缺口

如实登记，不做假覆盖：

1. **「不能禁用管理员账号」分支无法通过 HTTP 触达。**
   校验顺序是"不能操作自己(400)"先于"不能禁用管理员(403)"，
   而种子数据只有一个管理员，自己又不能禁用自己，所以第 2 条没有输入能走到。
   要覆盖需要补第二个管理员账号，或写直接改库的集成测试。
2. **更高并发（数十线程同一瞬间首建会话）未压测**，属未验证区域。
3. **外部图床**：种子头像依赖 `i.pravatar.cc`，离线时头像为破图（不影响功能）。
