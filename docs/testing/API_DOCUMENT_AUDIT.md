# API 文档审计报告

> 生成时间：2026-09-12
> 审计对象：`docs/api/**`（本次反向生成的接口文档）、`README.md`、`tests/README.md`、
> `docs/design.md`、`backend/src/main/java`（生产代码）、`tests/**`（pytest）、
> `backend/src/test/**`（JUnit）
> 审计原则：**以生产代码为唯一事实来源**。本次**未修改任何 Java / Python 代码、测试、
> 数据库结构与 `data.sql`**，只新增文档并记录差异。

---

> ## ⚠️ 本文件是 **2026-09-12 的历史快照**，未回改
>
> 保留原样是为了让审计结论可追溯。**其中部分问题已于 2026-09-14 修复**，
> 当前状态请以下表为准（也见 `docs/api/README.md` §12 与
> `docs/testing/TEST_COVERAGE_MATRIX.md` §7）：
>
> | 审计项 | 2026-09-14 状态 |
> |---|---|
> | `README.md` 接口数写 38（实际 50） | ✅ 已改为 50 |
> | pytest 数量（229 / 实测 201） | ✅ 实测 **246**，两份 README 已更正 |
> | 4 个被引用的 pytest 文件不存在 | ✅ 已补齐（`test_message_mutual_follow.py`、`test_conversation_mutual_follow.py`、`test_concurrency.py`、`test_file.py`，共 45 条） |
> | `test_cases/` 目录不存在 | ✅ 已建立最小体系（登记冻结规则，不与 pytest 1:1 复制） |
> | `docs/testing/` 缺 4 份测试文档、`docs/` 缺 `TESTING_GUIDELINES.md` | ✅ 已补齐 |
> | `POST /api/files/image` 零自动化覆盖 | ✅ 已由 `api/test_file.py`（14 条）覆盖 |
> | 互关私聊冻结规则缺 pytest 正负向回归 | ✅ 已补 25 条专项用例 |
> | 上传接口非 multipart / 缺 `file` 返回 500 | ✅ 已修（改为 400） |
> | `uk_users` 冲突日志文案误导 | ⚠️ 未改（功能正确，文案问题） |
> | `PUT /api/users/me/avatar` 不校验 `/uploads/` 前缀 | ✅ 已修（2026-09-14，BUG-005；pytest + JUnit 各 2 条回归，见 `bug_report.md`） |
> | `docs/design.md` §4 接口清单不全 | ⚠️ 未改 `design.md`；`README.md` 已改指向 `docs/api/README.md` |
>
> 下文正文保持 2026-09-12 原文不动。

---

## 0. 审计范围与结论摘要

| 项 | 数量 |
|---|---:|
| Controller | 16（`controller/` 10 个 + `controller/admin/` 6 个） |
| API Endpoint | **50** |
| 已文档化（`docs/api/`） | 50 / 50 |
| pytest 有直接断言 | **44** |
| pytest 仅间接覆盖（只作后台场景前置） | 1（`POST /api/reports`） |
| 仅 JUnit 有断言、pytest 无断言 | 4（`GET /api/health`、`GET /api/posts/{id}/likes`、`GET /api/users/{id}/following`、`GET /api/users/{id}/followers`） |
| **无任何自动化覆盖** | **1**（`POST /api/files/image`） |
| 发现的问题条目 | **19**（P0 4 / P1 8 / P2 7） |

**最严重的两件事**（先看这个）：

1. **`tests/` 与 `test_cases/` 的实际状态与文档严重不符**：
   `test_cases/` 目录**不存在**；`README.md` / `tests/README.md` 引用的
   4 个 pytest 文件（`test_message_mutual_follow.py`、`test_conversation_mutual_follow.py`、
   `test_concurrency.py`、`test_file.py`）**不在仓库中**；
   文档写 "pytest 229 个用例"，`pytest --collect-only` 实测 **201**。
2. **互关私聊规则的生产代码已实现，但 pytest 侧的正负向回归用例缺失**。
   非互关 403、解除互关后 403、重新互关恢复、存量非互关会话不可发送
   这几条**冻结规则**目前**没有任何 pytest 断言**（仅 JUnit 与手工验证覆盖）。

---

## 1. 文档 ↔ 代码不一致

### A-01【P0】`README.md` 接口总数写"38 个"，实际是 50 个

- **位置**：`README.md:501`「后端 38 个接口」。
- **实际**：`controller/**` 与 `controller/admin/**` 共 16 个 `@RestController`，
  映射方法（`@GetMapping/@PostMapping/@PutMapping/@DeleteMapping`）合计 **50** 个。
- **影响**：读者/面试官按文档核对会发现对不上；也说明该数字是管理后台扩展前统计的。
- **建议**：改为 50（或写明统计口径）。**本次未改代码，仅登记。**

### A-02【P0】`docs/design.md` 第 4 节接口清单只覆盖 27/50，且含 1 个不存在的接口

- **位置**：`docs/design.md`（第 4 节及第 939 行）。
- **问题 1（漏）**：文档中出现过的真实接口共 **27 个**，
  另有 **23 个**接口（`logout`、`GET /api/users/me` 全套、`me/password`、`me/avatar`、
  `me/following|followers`、`users/{id}/following|followers`、`users/search`、
  `GET /api/posts/{id}`、`posts/{id}/likes`、`GET /api/posts/{id}/comments`、
  `messages/unread-count`、`health`，以及管理后台的 9 个接口）
  **未出现在 design.md 的接口清单里**。
- **问题 2（多）**：`POST /api/admin/maintenance/rebuild-counts`（`design.md:939`）
  在代码中**不存在**（`grep` 全库无命中）。
  原文语气是"**建议**提供"，属设计建议；但清单式引用容易被当成已实现接口。
- **影响**：`README.md:359` 明确写"完整接口清单见 `docs/design.md` 第 4 节"——
  该指引会让读者拿到一份**不完整且含幻影接口**的清单。
- **建议**：把接口清单的权威来源改为 `docs/api/README.md`（本次新增，覆盖 50/50），
  或在 design.md 中标注"以下清单为设计期快照，实现以 `docs/api/` 为准"。

### A-03【P1】`PUT /api/users/me` 的接口描述与真实校验冲突

- **位置**：`UserController.updateProfile` 的 `@Operation(description = "仅支持修改昵称、性别、生日、简介。字段为 null 表示不修改。")`
- **实际**：`UpdateProfileRequest.nickname` 上有 `@NotBlank(message = "昵称不能为空")`，
  **不传 `nickname` 会返回 400**，而不会"跳过不修改"。
- **证据**：`tests/api/test_user.py::test_update_profile_validation` 的参数组
  `({"nickname": ""}, 400)` 正是钉住这一行为。
- **影响**：调用方按描述只传 `bio` 会被 400 拒绝，属于**契约级误导**。
- **建议**：描述改为"`nickname` 必填，`gender`/`birthday`/`bio` 为 null 表示不修改"。

### A-04【P2】`OpenApiConfig` 注释里的端口过时

- **位置**：`OpenApiConfig` 类注释「访问：`http://localhost:8080/swagger-ui.html`」。
- **实际**：`server.port = ${SERVER_PORT:8081}`，默认 **8081**（8080 被本机 Windows 服务占用，见 `application.yml` 注释）。
- **影响**：仅注释误导，非功能问题。

### A-05【P2】`Avatar` 接口不校验 URL 前缀，与动态图片规则不一致

- **实际**：`PostServiceImpl.validateImageUrls` 要求动态图片 URL 必须 `startsWith("/uploads/")`，
  否则 `400 INVALID_IMAGE_URL`；而 `PUT /api/users/me/avatar`（`UpdateAvatarRequest`）
  **只校验非空与长度**，传外链（`https://…`）也能成功。
- **影响**：同为"图片 URL"，两处规则不同，测试容易按动态的规则去写头像断言而误判失败。
- **建议**：明确这是有意的差异（头像允许外链？）还是应统一。

### A-06【P2】`POST /api/auth/logout` 的行为容易被误判为 Bug

- **实际**：JWT 无状态，服务端不维护黑名单；该接口**只写审计日志，不使 Token 失效**。
- **影响**：测试若写"退出后旧 Token 应 401"会失败，且看起来像安全漏洞。
- **状态**：属**有意的设计取舍**（`README.md`「设计偏离说明 #7」已记录），
  本文档在 [authentication.md](./api/authentication.md) 中已显式标注"别误判为 Bug"。

---

## 2. 测试资产与文档不一致（本次最严重）

### B-01【P0】`test_cases/` 目录不存在

- **文档预期**：任务书与早前阶段产出中，`test_cases/<module>/<module>.yaml`（13 个模块、184 条 YAML 用例）
  与 `docs/testing/TEST_CASE_SCHEMA.md`、`TEST_COVERAGE_MATRIX.md`、`MIQU_TEST_SYSTEM.md`、
  `docs/TESTING_GUIDELINES.md` 均有引用关系。
- **实际**：`test_cases/` **不存在**；`docs/` 下只有 `design.md` 一个文件。
- **连带影响**：
  1. **"API → 测试用例 ID"的映射无法建立**。
     本次 `docs/api/**` 只能建立到 **pytest 测试函数 / JUnit 测试类** 的映射
     （即 `API → 业务场景 → pytest 函数`），**缺少 `测试用例 ID` 这一层**。
  2. 早前设计的 YAML ID 体系（`AUTH-001`、`MSG-002`、`CONV-003` 等）**当前无载体**。
- **建议（需你决策）**：二选一——
  ① 恢复 `test_cases/`（若视为测试设计资产）；
  ② 明确废弃 YAML 体系，以 `docs/api/**` + pytest 函数名作为唯一追溯链。

### B-02【P0】4 个被文档引用的 pytest 文件不存在，用例总数 229 vs 实测 201

| 文件 | 文档记载 | 实际 |
|---|---|---|
| `tests/api/test_message_mutual_follow.py` | 10 用例（互关私聊规则） | **不存在** |
| `tests/api/test_conversation_mutual_follow.py` | 4 用例（会话互关规则） | **不存在** |
| `tests/api/test_concurrency.py` | 5 用例（并发） | **不存在** |
| `tests/api/test_file.py` | 9 用例（文件上传安全） | **不存在** |

- **证据**：`find tests -name "*.py"` 只有 13 个文件（9 个测试文件 + `conftest.py` + 2 个 `__init__.py` + `utils/client.py`）；
  `python -m pytest tests --collect-only -q` → **201 tests collected**。
- **文档现状**：`README.md` 与 `tests/README.md` 均写 **229 个用例**，
  且逐行列出了上述 4 个文件（`README.md:438`、`tests/README.md:104-113`）。
- **影响**：
  1. 用例数字错误（229 → 应为 201）；
  2. **互关私聊规则的回归保护实际缺失**（见 C-01）；
  3. 文件上传安全测试实际为零（见 C-02）。

### B-03【P1】`README.md` 内部数字自相矛盾

- **位置**：`README.md:498` 实现进度表 P6 写「pytest 接口自动化（**201 用例**）」，
  而同一文件 `README.md:438` 与 `README.md:501` 写 **229**。
- **实际**：201 是对的（与 `--collect-only` 一致）。

### B-04【P1】后台访问控制矩阵：pytest 是 7 个接口，"11"其实是 JUnit 的数字

- **文档**：`tests/README.md:107` 在 **pytest 文件 `api/test_admin.py`** 那一行写
  「访问控制矩阵（**11 个后台接口** × 未登录 401 / 非管理员 403 / 管理员 200）」。
- **实际（pytest）**：`tests/api/test_admin.py` 的 `ADMIN_ENDPOINTS` 常量只有 **7 个 GET 接口**：
  `stats`、`users`、`users/2`、`posts`、`comments`、`reports`、`logs`。
  4 个写接口（`PUT users/{id}/status`、`DELETE posts/{id}`、`DELETE comments/{id}`、
  `PUT reports/{id}/handle`）**不在 pytest 矩阵中**。
- **实际（JUnit）**：`AdminAccessControlTest` **确实覆盖 11 个接口**
  （上面 7 个 + `posts/1`、`comments/1`、`users/2/status`、`reports/1/handle`），
  但它只有 **4 个 `@Test` 方法**（内部循环遍历端点列表）。
- **结论**：这个 "11" 很可能是**从 JUnit 串到 pytest 行**的笔误。
  两边都不是"11 个接口 × 4 个用例"的独立用例数。
- **影响**：会让人误以为 pytest 侧覆盖了后台写接口的 401/403。
- **建议**：把 `tests/README.md` 该行的"11"改为 7，并单独说明 JUnit 侧的 11。

### B-05【P2】pytest 侧的实际分布与文档不符

- 实测 201 个用例分布在 9 个文件，文档列了 13 个文件。
- 逐文件实测（`def test_` 计数 + 参数化展开）与 `AC-ADMIN` 参数化（7 接口 × 3）有关，无法与文档一一对应。
- **建议**：把用例数写进 CI 产出的统计，而不是手写到 README。

---

## 3. 缺测试覆盖的接口（API ↔ 测试不一致）

### C-01【P0】互关私聊的**负向**规则完全没有 pytest 断言

冻结规则（`design.md` 决策 16、`README.md` 6.5 节）要求下列行为被自动化钉死：

| 规则 | 生产代码 | pytest | JUnit |
|---|---|---|---|
| 非互关发送消息 → 403 `NOT_MUTUAL_FOLLOW` | ✅ 已实现 | ❌ **无** | 部分（前置改造） |
| 单向关注发送 → 403 | ✅ | ❌ **无** | ❌ |
| 解除互关后发送 → 403 | ✅ | ❌ **无** | ❌ |
| 重新互关后恢复发送 → 200 | ✅ | ❌ **无** | ❌ |
| 非互关打开会话 → 403 | ✅ | ❌ **无** | ❌ |
| 解除互关后重新打开会话 → 403 | ✅ | ❌ **无** | ❌ |
| 解除互关后历史消息仍可读 → 200 | ✅ | ❌ **无** | ❌ |
| 解除互关后仍可标记已读 → 200 | ✅ | ❌ **无** | ❌ |
| 存量非互关会话（种子 conv 4/5）可读不可发 | ✅ | ❌ **无** | ❌ |

- **生产代码位置**（已核实存在）：
  - `ErrorCode.NOT_MUTUAL_FOLLOW(403, "需要互相关注后才能私聊")`
  - `FollowStatusLoader.isMutual(a, b)`
  - `MessageServiceImpl.send`：`if (!followStatusLoader.isMutual(userId, receiverId)) throw …`
  - `ConversationServiceImpl.openConversation`：同样的校验
- **现状**：`tests/api/test_message.py` 里只有 `_make_mutual()` **前置构造 helper**
  （为既有正向用例补互关前置），它**不测试**规则本身。
- **风险等级：高**——这是一条**冻结的业务规则**，却没有任何负向回归保护；
  后续重构（例如把校验挪位置、误加到 `listMessages`）不会被测试发现。
- **建议**：优先恢复/新建互关专项 pytest（对应 `MSG-001..008`、`CONV-001/003/007/008` 场景）。

### C-02【P0】文件上传接口没有任何自动化测试

- **`POST /api/files/image`**：pytest **无**用例（`test_file.py` 不存在），
  JUnit 侧也**没有**对应的上传测试类。
- **影响**：魔数校验、5MB 上限、UUID 重命名、未登录拦截这些**安全相关行为**全无自动化保护。
- **建议**：补正常上传 / 空文件 / 超限 / 伪后缀 / 极小文件 / 未登录 6 条。

### C-03【P1】`GET /api/health` 无独立断言用例

- 仅被 `tests/conftest.py::ensure_backend_running` 作为**会话级前置探测**（`code != 200` 则 `pytest.exit`）。
- JUnit 有 `HealthControllerTest`（4 用例）覆盖结构、数据库 UP/DOWN、免登录。
- **建议**：pytest 补 1 条结构断言（`status/application/database` 均为字符串、免登录 200）。

### C-04【P1】三个接口仅有 JUnit 断言、pytest 完全没有触碰

| 接口 | pytest | JUnit |
|---|---|---|
| `GET /api/posts/{id}/likes` | ❌ 从未被调用 | `PostLikeControllerTest` |
| `GET /api/users/{id}/following` | ❌ 只测了 `/me/following` | `FollowControllerTest` |
| `GET /api/users/{id}/followers` | ❌ 只测了 `/me/followers` | `FollowControllerTest` |

- **证据**：`grep -rn "/following\|/followers" tests/api/*.py` 只命中
  `/api/users/me/following` 与 `/api/users/me/followers`。
- **建议**：补 3 条（种子 `post 1` 的 `total == 10`；`/api/users/2/following` 为 6、
  `/api/users/2/followers` 为 14；目标不存在 404；游客可访问）。

### C-05【P1】`POST /api/reports` 的用户侧断言只在 JUnit

- pytest **没有 `test_report.py`**。`tests/api/test_admin.py` 只是把举报当作
  **后台场景的前置数据**来创建，并断言 `targetPreview` 等**后台视角**字段；
  未断言"不能举报自己 / 重复举报 409 / 目标不存在 404 / 参数非法 400"这些**用户侧**契约。
- JUnit 侧 `ReportControllerTest`（13 用例）有覆盖。
- **建议**：补 pytest 侧 4~5 条，使举报的用户侧契约在端到端层也被守住。

### C-06【P2】后台写接口缺少"未登录 401 / 非管理员 403"矩阵验证

见 B-04。写接口的业务用例里含"普通用户被拒"的断言（部分），
但没有统一的矩阵式覆盖。

### C-07【P2】若干"未登录 401"未被 pytest 单独覆盖

| 接口 | pytest 是否覆盖 401 |
|---|---|
| `PUT /api/users/me/password` | ❌ |
| `PUT /api/users/me/avatar` | ❌ |
| `DELETE /api/posts/{id}/like` | ❌ |
| `GET /api/conversations/{id}/messages` | ✅（非成员 403 已覆盖，401 未单独断言） |
| `POST /api/conversations` | ❌ |

- **说明**：白名单是"失败关闭"策略且已有 JUnit 覆盖，风险不高；属补强项。

---

## 4. 参数定义不清晰 / 容易误判的点（已在本次文档中显式说明）

### D-01【P1】错误优先级未在文档中定义（现已由代码推导并写入文档）

互关校验与"目标不存在 / 被禁用 / 是自己"的**先后顺序**决定返回哪个错误码：

| 组合 | 实际返回 |
|---|---|
| 非互关 + 接收者/目标不存在 | `404 USER_NOT_FOUND` |
| 非互关 + 接收者/目标被禁用 | `423 USER_DISABLED` |
| 非互关 + 目标是自己 | `400 CANNOT_MESSAGE_SELF` |
| 非互关 + 内容为空 | `403 NOT_MUTUAL_FOLLOW`（互关校验在内容校验**之前**） |
| 非互关 + 目标正常 | `403 NOT_MUTUAL_FOLLOW` |

- **现状**：任何文档都没写过这张表，此前阶段也是一处"待拍板"项。
- **本次处理**：已在 [messages.md](./api/messages.md) 与 [conversations.md](./api/conversations.md)
  中写出"校验顺序"与优先级表（**依据源码顺序**，非猜测）。
- **建议**：把这张表同步进 `design.md`，作为契约的一部分。

### D-02【P2】`keyword` 缺失 vs 为空的两种 400 文案

- `GET /api/users/search`：
  - 完全不传 `keyword` → `400 缺少必要参数：keyword`；
  - 传空串 / 纯空格 → `400 搜索关键词不能为空`；
  - 传 33 字符 → `400 搜索关键词不能超过 32 个字符`。
- **影响**：断言文案时需区分路径。

### D-03【P2】`tab=following` 未登录的 401 来自 Service 而非拦截器

- `GET /api/posts` 在**白名单**里（游客可浏览 `latest`），
  因此 `tab=following` 的 401 是 `PostServiceImpl.listFeed` 主动抛的 `UNAUTHORIZED`，
  **不是** `AuthInterceptor` 拦的。
- **影响**：排查"为什么这个接口在白名单里还会 401"时需要知道这一点。

### D-04【P2】"取消关注 / 取消点赞"返回 404 而不是幂等 200

- `DELETE /api/users/{id}/follow` 未关注 → `404 NOT_FOLLOWED`；
- `DELETE /api/posts/{id}/like` 未点赞 → `404 NOT_LIKED`。
- 属有意取舍（`README.md`「设计偏离说明 #2」），但**测试容易按"幂等"预期写 200**。

### D-05【P2】`CommentVO` 没有 `userId` 字段

- 评论作者只能从 `author.id` 读取，或用 `mine` 判断。
- 测试想断言"这条评论是谁发的"需注意字段路径。

---

## 5. 错误码一致性

### E-01【P1】`EMPTY_COMMENT` 是死枚举（不可达）

- `ErrorCode.EMPTY_COMMENT(400, "评论内容不能为空")`：经全库扫描，
  **没有任何 `ErrorCode.EMPTY_COMMENT` 引用**（唯一一个未被引用的枚举）。
- **原因**：`CommentCreateRequest.content` 的 `@NotBlank` 会先返回
  `PARAM_INVALID` + 同一句文案，Service 侧没有对应的空值分支。
- **影响**：文案相同、**枚举名不同**，但响应里只有 `code` 与 `message`，外部不可区分；
  维护者可能误以为存在该分支。

### E-02【P1】`INVALID_TARGET_TYPE` 在 HTTP 层不可达

- `CreateReportRequest.targetType` 有 `@Min(1) @Max(3)`，
  传 `4` 会先被 Bean Validation 拦成
  `400 PARAM_INVALID / "举报目标类型只能是 1、2 或 3"`；
  而 Service 里 `!TargetTypeEnum.isValid(...) || == REPORT(4)` 的分支拿不到输入。
- **影响**：文档里若写"传 4 返回 `举报目标类型不合法`"会与实测不符。
  本次文档已写明**实际文案**并标注该分支不可达。

### E-03【P2】`TOO_MANY_IMAGES` 与 `@Size` 文案重复、枚举不同

- 9 张上限在 DTO（`@Size(max = 9)`，`PARAM_INVALID`）与
  Service（`TOO_MANY_IMAGES`）各校验一次，**message 完全相同**（`最多只能上传 9 张图片`）。
- 同上，外部不可区分，属"双保险"设计（有意），但会造成错误码归属歧义。

### E-04【P2】`405` 不在 `ErrorCode` 枚举内

- `GlobalExceptionHandler.handleMethodNotSupported` 直接 `Result.fail(405, "请求方法不支持：" + method)`，
  使用 `Result.fail(int, String)` 重载，**未定义枚举**。
- **影响**：按枚举清单写测试会漏掉 `405`。

### E-05【P2】`NOT_FOUND` 的文案被局部覆盖

- `ReportServiceImpl.handle`：`throw BizException.of(ErrorCode.NOT_FOUND, "举报不存在")`
  → 实际 message 是 `举报不存在`，而不是枚举默认的 `资源不存在`。
- **影响**：断言 message 时要看具体分支；本次文档已在 [admin.md](./api/admin.md) 标注。

### E-06【P2】响应信封里没有 `errorCode` 名称字段

- 响应只有 `code`（数字）与 `message`（中文）。**枚举名（如 `NOT_MUTUAL_FOLLOW`）不出现在响应中**，
  它是源码内的标识。
- **影响**：任务描述里把它作为"业务错误码"引用，但接口层面只能通过 `403` + `需要互相关注后才能私聊` 断言。
  若希望测试能按枚举名断言，需要**改代码**（本次未改）。**这一条需要你确认口径**。

---

## 6. HTTP 状态码一致性

### F-01【已确认一致】业务失败一律 HTTP 200

- `GlobalExceptionHandler` 所有分支返回 `Result`，**没有 `@ResponseStatus`**，
  因此参数错误、未登录、无权限、不存在、冲突、禁用**全部是 HTTP 200**，业务码在 body 里。
- `tests/utils/client.py` 的注释已把这一点固化为全项目约定。
- **风险**：外部测试人员若按 REST 直觉断言 HTTP 4xx/5xx，会大面积误判。
  本次在 [API_CONVENTIONS.md](./api/API_CONVENTIONS.md#8-http-状态码约定) 用加粗警告写明。
- **唯一的"设计内"例外**：`GET /api/health` 在数据库不可用时返回 `code=500`，
  但 **HTTP 仍是 200**。

### F-02【P2】`GET /api/health` 的 500 属特例

- 它是唯一在失败时**仍返回 `data`** 的接口（`Result.fail(code, msg, data)`）。
- 已在 [system.md](./api/system.md) 中说明。

---

## 7. 测试名称与断言不符

### G-01【P1】`test_me_returns_403_after_account_disabled` 名称写 403，实际断言 423

- **位置**：`tests/api/test_auth.py:140`。
- **实际断言**：`victim.get("/api/users/me").code == 423`（账号禁用 → `USER_DISABLED`），
  以及 `victim.login(...).code == 423`。
- **影响**：名称会误导后续维护者以为账号禁用返回 403（那是"非管理员"的码）。
- **建议**：重命名为 `..._returns_423_after_account_disabled`（**属测试改动，本次未做**）。

---

## 8. 未发现问题的地方（澄清）

以下几处**看起来像**问题，但经核对**代码与文档一致**，无需处理：

| 项 | 结论 |
|---|---|
| `GET /api/posts` 在白名单里却能返回 401 | 有意：`latest` 游客可用，`following` 由 Service 拦（D-03） |
| `POST /api/auth/logout` 不使 Token 失效 | 有意取舍，已记录在 README |
| 取消关注/取消点赞返回 404 | 有意取舍，已记录在 README |
| 分页越界返回 400 而非静默重置 | 有意取舍（`PageQuery` 注释） |
| `conversation` 空会话不出现在列表 | 有意设计（避免空白条目） |
| `report` 表无 `deleted` 列、实体不继承 `@TableLogic` | 已被 `EntitySchemaConsistencyTest` 钉住 |
| JUnit 289 个 `@Test` | 实测 289，与 README 一致 ✅ |
| 管理后台 401/403 文案不同 | 有意设计（可区分），已被用例断言 |

---

## 9. 建议处理清单（按优先级；本次**未做任何代码/测试改动**）

| 优先级 | 事项 | 类型 |
|---|---|---|
| **P0** | 决策 `test_cases/` YAML 体系去留（B-01） | 决策 |
| **P0** | 恢复或新建互关私聊专项 pytest（C-01） | 补测试 |
| **P0** | 补 `POST /api/files/image` 的上传测试（C-02） | 补测试 |
| **P0** | 修正 README / tests/README 的接口数与用例数（A-01、B-02、B-03、B-04） | 改文档 |
| P1 | 修正 `PUT /api/users/me` 的接口描述（A-03） | 改代码注释或文档 |
| P1 | 补 `health`、`posts/{id}/likes`、`reports` 的 pytest（C-03~C-05） | 补测试 |
| P1 | 把"错误优先级表"写进 `design.md`（D-01） | 改文档 |
| P1 | 清理死枚举 `EMPTY_COMMENT`（E-01）/ 明确其保留理由 | 改代码 |
| P1 | 重命名误导性的测试函数（G-01） | 改测试 |
| P2 | `design.md` 接口清单对齐 `docs/api/`（A-02） | 改文档 |
| P2 | `OpenApiConfig` 端口注释（A-04） | 改注释 |
| P2 | 头像是否校验 URL 前缀（A-05） | 决策 |
| P2 | 是否在响应中增加枚举名字段（E-06） | 决策（需改代码） |

---

## 10. 结论：当前 API 文档是否可直接用于测试人员做接口测试？

**可以用于"接口测试"，但有两个前提。**

### 可以直接用的部分 ✅

- `docs/api/**` 覆盖 **50/50** 个接口，逐个给出：
  method / URL / 认证要求 / 参数表（区分 Path、Query、Body）/ 真实 JSON 示例 /
  成功与失败响应 / **精确到文案的错误码表** / 触发条件 / 测试关注点 / 对应的 pytest 与 JUnit 测试。
- 通用约定（响应信封、**HTTP 恒 200 的坑**、错误码全集、分页、序列化、枚举字典、
  长度上限、唯一键冲突映射、CORS、文件访问）集中在
  [API_CONVENTIONS.md](./api/API_CONVENTIONS.md)，测试人员不必翻代码。
- 私有规则（互关私聊）给出了完整规则表、校验顺序与错误优先级。

### 两个前提 ⚠️

1. **测试数据基线要以实测为准，不要抄 README 的数字**：
   pytest 实际 **201** 个用例（不是 229），`test_cases/` 目录当前不存在，
   4 个被引用的 pytest 文件不存在。
2. **"API → 测试用例 ID"这层映射当前不可用**：
   由于 `test_cases/*.yaml` 缺失，本次只能建立
   `API 端点 → 业务场景 → pytest 测试函数 / JUnit 测试类` 的两级映射。
   若要恢复 `MSG-002` 这类 ID 级追溯，需要先恢复 YAML 资产（或明确废弃该体系）。

> 也就是说：**文档本身已可用于按接口逐条编写与执行测试**；
> 但"用例 ID ↔ 端点"的追溯链与**互关私聊的回归保护**仍是缺口，
> 这两项不是文档问题，而是测试资产缺失，需要你决定如何补齐。
