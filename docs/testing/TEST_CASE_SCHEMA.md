# YAML 测试用例规范（TEST_CASE_SCHEMA）

本文件定义 `test_cases/` 下 YAML 用例的**字段、ID 规则与维护约定**。

先明确定位，避免误解：

> **YAML 是测试设计与用例管理文档，不参与执行。**
> 可执行的**事实来源是 pytest / JUnit**。YAML 不与 pytest 做 1:1 复制——
> 那样等于维护两份真相，改一处忘一处就会自相矛盾。

---

## 1. 文件组织

```
test_cases/
├── README.md                          # 使用说明与维护约定
├── core_business_rules.yaml           # 冻结的核心业务规则（最高优先维护）
├── security_and_concurrency.yaml      # 安全与并发
└── api_smoke.yaml                     # 冒烟集合（对外演示 / CI 首轮）
```

**不为每个 pytest 文件建一份 YAML**，也不追求用例条数。
登记标准（满足其一才登记）：

- 属于**冻结的业务规则**（改了要有人拍板，例如互关私聊）；
- 属于**安全边界**（越权、注入、上传）；
- 属于**对方具备破坏力的场景**（并发、重复提交）；
- 属于**对外演示必须走通的冒烟路径**。

普通的参数校验、字段边界这类，由 pytest 覆盖即可，不重复登记。

---

## 2. 用例字段

```yaml
- id: MSG-003                      # 必填，见 §3 ID 规则
  title: 单向关注不能发送私信        # 必填，一句话说清验的是什么
  priority: P0                    # 必填，P0 / P1 / P2
  type: business_rule             # 必填，见 §4
  rule: 私信要求双方互相关注         # 规则原文（冻结规则必填）
  frozen_at: 2026-09-11           # 规则冻结日期（business_rule 必填）

  preconditions:                  # 前置条件，字符串数组
    - 用户 A、B 均为新注册用户
    - A 已关注 B，B 未回关

  steps:                          # 操作步骤，字符串数组（可执行的自然语言）
    - A 向 B 发送私信，内容为 "你好"

  expected:                       # 期望结果
    code: 403                     # 后端业务码（HTTP 恒 200，只看 body 的 code）
    message: 需要互相关注后才能私聊  # 精确文案（能唯一确定时必填）
    effect: 不产生会话、不产生未读、不落消息库

  pytest: api/test_message_mutual_follow.py::test_one_way_follow_cannot_send
                                  # 对应的可执行用例；未实现时写 null 并填 gap 原因
  status: implemented             # implemented / gap
  gap_reason: null                # status=gap 时必填
```

### 字段速查

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `id` | ✅ | string | 见 §3 |
| `title` | ✅ | string | 一句话描述 |
| `priority` | ✅ | enum | `P0` 冻结规则/安全；`P1` 主要流程；`P2` 边界 |
| `type` | ✅ | enum | 见 §4 |
| `rule` | 条件 | string | `business_rule` 必填 |
| `frozen_at` | 条件 | date | `business_rule` 必填 |
| `preconditions` | ✅ | list | 前置条件 |
| `steps` | ✅ | list | 步骤 |
| `expected.code` | ✅ | int | 业务码 |
| `expected.message` | 条件 | string | 文案唯一确定时必填 |
| `expected.effect` | 否 | string | 副作用（如"未落库"） |
| `pytest` | ✅ | string/null | `文件::函数名`；无对应用例写 `null` |
| `browser` | 否 | string/null | **前端用例专用**：`browser_regression.mjs::BUG-002`（支持用例级前缀匹配）；用了它时 `pytest` 写 `null` |
| `status` | ✅ | enum | `implemented` / `gap` |
| `gap_reason` | 条件 | string | `status=gap` 必填 |

---

## 3. ID 规则

格式：`<域前缀>-<三位序号>`，例如 `MSG-003`。

| 前缀 | 域 | 对应 pytest 文件 |
|---|---|---|
| `AUTH` | 注册 / 登录 / 令牌 | `test_auth.py` |
| `USER` | 个人资料 / 密码 / 头像 | `test_user.py` |
| `FOL` | 关注关系 | `test_follow.py` |
| `POST` | 动态发布与列表 | `test_post.py` |
| `LIKE` | 点赞 | `test_post.py` |
| `CMT` | 评论 | `test_comment.py` |
| `CONV` | **会话（打开/成员/已读）** | `test_conversation_mutual_follow.py`、`test_message.py` |
| `MSG` | **私信发送** | `test_message_mutual_follow.py`、`test_message.py` |
| `NOTI` | 通知 | `test_notification.py` |
| `SRCH` | 搜索 | `test_search.py` |
| `FILE` | 文件上传 | `test_file.py` |
| `SEC` | 越权 / 认证 / 注入 | 跨文件 |
| `CONC` | 并发 | `test_concurrency.py` |

### 编号纪律

1. **一个 ID 只描述一件事**，一经分配**不再复用**、不再改含义。
2. 用例删除时，ID **作废保留**（不要拿旧编号给新用例），并在该条注明 `status: gap`。
3. **同一行为只登记一次。** `CONV` 与 `MSG` 的分界是：

   | | 管的是 | 入口 |
   |---|---|---|
   | `CONV-*` | 会话本身的打开 / 成员身份 / 已读 | `POST /api/conversations`、`PUT /{id}/read` |
   | `MSG-*` | 消息的发送 / 读取 | `POST /api/messages`、`GET /{id}/messages` |

   > 这条分界是有意写死的。此前 `CONV-007` / `CONV-008` 出现过"同一行为
   > 被两条 ID 各描述一遍"的冲突，根因就是没定分界。
   > 现在规则是：**凡"能不能发/能不能读消息"归 `MSG`，
   > 凡"能不能打开会话/是不是会话成员"归 `CONV`。**

---

## 4. `type` 取值

| 值 | 含义 | 典型例子 |
|---|---|---|
| `business_rule` | 冻结的业务规则，改它需要拍板 | 互关才能私聊 |
| `security` | 越权、注入、上传、认证 | 非成员读会话、魔数校验 |
| `concurrency` | 竞态与唯一键兜底 | 并发重复点赞 |
| `smoke` | 对外演示必须走通的主路径 | 登录、发动态、发私信 |
| `boundary` | 参数与数量边界 | 内容 1000/1001 字 |

---

## 5. 与 pytest 的一致性校验

YAML 不会自动执行，所以需要**人工核对**+**脚本兜底**。核对三件事：

```
YAML 的 id  ──→  pytest 字段指向的函数是否真实存在（文件 + 函数名都要对）
            ──→  该函数的实际断言是否与 expected 一致（尤其是 code 与 message）
            ──→  该函数对应的业务行为是否与 rule 描述一致
```

命令辅助（列出所有真实存在的用例函数，逐条比对）：

```bash
cd tests && python -m pytest --collect-only -q
```

### 5.1 前端用例：`browser` 字段

有些缺陷**pytest 表达不出来**——后端每次返回都是对的，错在前端没有把请求发出去
（例如"点了筛选条件却没重新取数"）。这类用例只在真实浏览器里可见，
因此登记为 `browser` 指针，指向 `tests/browser_regression.mjs` 里的断言 ID：

```yaml
    pytest: null
    browser: browser_regression.mjs::BUG-002     # 可命中 BUG-002.a / .b / .c
    status: implemented
```

指针支持**用例级前缀匹配**（一个 Bug 常对应多条断言，不必逐条枚举），
但前缀写错会匹配不到任何 ID，检查照样报错。

### 5.2 自动校验

```bash
python test_cases/check_consistency.py
```

脚本核对四件事：YAML 可解析 / `id` 格式与全局唯一 / `pytest` 指针存在 / `browser` 指针存在。
退出码 0 才算通过——它把"YAML 说是这样、代码其实那样"这类漂移挡住。

`docs/testing/TEST_COVERAGE_MATRIX.md` 记录了最近一次核对的结论。

> **红线**：不允许出现 "YAML 说 403、pytest 断言 200" 这类矛盾。
> 一旦发现，以**实测行为**为准修正文档；如果实测行为本身是错的，
> 先修代码，再同时更新文档与用例。

---

## 6. 什么时候该动 YAML

| 场景 | 动作 |
|---|---|
| 新增/修改冻结业务规则 | 必改：更新对应条目并同步 `frozen_at` |
| 新增接口或安全边界 | 视情况新增条目（按 §1 登记标准） |
| 用例实现补齐（`gap` → `implemented`） | 必改：填 `pytest`、改 `status`、清空 `gap_reason` |
| 只调了参数边界值 | **不用改 YAML**（那属于 pytest 的职责） |
| 只是为了"用例数好看" | **不要改**。数量不是目标 |
