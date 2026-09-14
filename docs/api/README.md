# Miqu「觅取」社交系统 —— API 接口文档

本目录是 Miqu 后端接口的**实测文档**：内容由当前生产代码
（Controller / DTO / VO / Service / `ErrorCode` / 拦截器 / 过滤器）反向整理，
并逐条与现有 pytest 自动化测试建立映射。

> 先读 [API_CONVENTIONS.md](./API_CONVENTIONS.md)：响应信封、错误码全集、
> 分页、序列化、枚举字典都在那里，各模块文档只写该模块特有的内容。
>
> ⚠️ **最先要记住的一条**：业务失败时 **HTTP 状态码仍然是 `200`**，
> 成功与失败看响应体的 `code`。断言 HTTP 状态码是最常见的误判来源。

---

## 1. 文档说明

| 项 | 值 |
|---|---|
| 事实来源 | `backend/src/main/java`（生产代码），非设计稿 |
| 生成时间 | 2026-09-12 |
| 接口总数 | **50** 个（16 个 Controller，含 `controller/admin/` 子包 6 个） |
| 已文档化 | 50 / 50 |
| 已知问题 | 见 [`docs/testing/API_DOCUMENT_AUDIT.md`](../testing/API_DOCUMENT_AUDIT.md) |

**本文档不做的事**：不发明接口、不改代码、不修饰行为。
若代码与任何文档冲突，以代码为准，并把冲突登记进审计报告。

---

## 2. Base URL

```text
http://localhost:8081
```

- 无上下文路径（未配置 `context-path`），因此接口路径就是 `/api/...`。
- 默认端口 `8081`（可用 `SERVER_PORT` 覆盖）。
- 前端开发环境通过 Vite 代理把 `/api` 转发到 `8081`，浏览器侧同源可写 `/api/...`。

---

## 3. 认证方式

**JWT（HS256）无状态认证**，详见 [API_CONVENTIONS.md §2](./API_CONVENTIONS.md#2-认证方式jwt--bearer)。

- 有效期 7 天（604800 秒），响应中的 `expiresIn` 即此值。
- 服务端**不维护会话**，`POST /api/auth/logout` **不会让 Token 失效**。
- 认证过滤器**每次请求查库校验账号状态**：用户被禁用后，旧 Token **立即**失效（返回 `423`）。
- 免登录白名单共 12 条，策略是**失败关闭**（不在白名单的 `/api/**` 一律要登录）。

---

## 4. Token 使用方式

```http
GET /api/users/me HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyIiwicm9sZSI6MX0.xxxx
Accept: application/json
```

- 前缀固定 `Bearer `（含一个空格）。**缺少前缀或格式错误一律按未登录处理（401）**。
- Swagger UI（`/swagger-ui.html`）已注册 `bearerAuth` 方案，
  点 Authorize 后**只填 token 本身**，不用手写 `Bearer`。

---

## 5. 通用请求格式

| 项 | 约定 |
|---|---|
| 编码 | `UTF-8`（`force-request: true`） |
| JSON 请求 | `Content-Type: application/json` |
| 表单上传 | `Content-Type: multipart/form-data`（字段名 `file`） |
| 查询参数 | 直接拼在 URL 上，如 `?page=1&size=10` |
| 路径参数 | 用户/动态/评论/会话/通知/举报 ID |
| 未知 JSON 字段 | 忽略（不会报错） |
| 请求体类型错误 | `code=400`，`message="请求体格式不正确"` |
| 路径参数格式错误 | `code=400`，`message="参数格式不正确：id"` |

---

## 6. 通用响应格式

```json
{ "code": 200, "message": "success", "data": { } }
```

- `code`：业务状态码，**判断成功失败只看它**。
- `message`：成功恒为 `"success"`；失败为面向调用方的中文文案。
- `data`：业务数据；失败通常为 `null`（健康检查是唯一例外，见 `system.md`）。

分页响应的 `data` 使用统一结构（`PageResult`）：

```json
{ "list": [], "total": 128, "page": 1, "size": 10, "hasNext": true }
```

---

## 7. 通用错误码

完整清单（含枚举名与精确文案）见
[API_CONVENTIONS.md §4](./API_CONVENTIONS.md#4-业务错误码全集errorcode)。摘要：

| code | 语义 | 典型场景 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数错误 | 校验失败、越界、跨字段规则不满足 |
| 401 | 未登录 | 未带 Token / Token 无效过期 / 账号已注销 |
| 403 | 无权限 | 删除他人内容、非会话成员、**非互关私聊**、非管理员访问后台 |
| 404 | 不存在 | 用户/动态/评论/会话/通知/举报不存在；也用于"取消关注/取消点赞时本来就没有" |
| 409 | 冲突 | 重复注册/关注/点赞/举报、举报重复处理、会话并发创建 |
| 423 | 账号禁用 | 登录、以及携带旧 Token 访问 |
| 500 | 系统异常 | 未预期异常；健康检查在数据库不可用时也用它 |

---

## 8. HTTP 状态码约定

| HTTP | 何时出现 |
|---|---|
| **200** | **几乎所有情况**，包括业务失败。业务结果在响应体 `code` 里 |
| 200 | 静态图片 `GET /uploads/**`、Swagger UI |

> 也就是说：**本项目不能用 HTTP 状态码区分成功与失败**。
> `GlobalExceptionHandler` 的所有分支都返回 200 + 业务码。
> 测试断言请统一写 `assert resp.code == 4xx`，而不是 `resp.status_code`。

---

## 9. API 模块索引

| 模块 | 文档 | 端点数 | 说明 |
|---|---|---:|---|
| 认证 | [authentication.md](./authentication.md) | 3 | 注册、登录、退出 |
| 用户 | [users.md](./users.md) | 7 | 当前用户、资料、头像、改密码、主页、某用户动态、搜索 |
| 关注 | [follows.md](./follows.md) | 6 | 关注、取消关注、"我的"与"某用户的"关注/粉丝列表 |
| 动态 | [posts.md](./posts.md) | 7 | 发布、列表、详情、删除、点赞、点赞列表 |
| 评论 | [comments.md](./comments.md) | 3 | 发表、列表、删除 |
| 会话 | [conversations.md](./conversations.md) | 4 | 会话列表、打开/创建、聊天记录、标记已读 |
| 私信 | [messages.md](./messages.md) | 2 | 发送私信、未读总数 |
| 通知 | [notifications.md](./notifications.md) | 4 | 列表、未读数、单条已读、全部已读 |
| 举报 | [reports.md](./reports.md) | 1 | 提交举报 |
| 文件 | [files.md](./files.md) | 1 | 图片上传 |
| 系统 | [system.md](./system.md) | 1 | 健康检查 |
| 管理后台 | [admin.md](./admin.md) | 11 | 统计、用户、动态、评论、举报、日志 |

> 归类原则：按**业务模块**而非 Controller 文件。`Controller` 的物理分布与模块并不一一对应：
> `UserController` 同时承载"用户"与"关注"两块（关注相关接口写在 [follows.md](./follows.md)）；
> `ConversationController` 与 `MessageController` 是私信业务的两个半边
> （会话 → [conversations.md](./conversations.md)，发送与未读 → [messages.md](./messages.md)）；
> 管理后台的 6 个 Controller 合并在 [admin.md](./admin.md)。
> `CommentController` 的路径基址是 `/api`（不是 `/api/comments`），
> 因为它同时提供 `POST/GET /api/posts/{id}/comments` 与 `DELETE /api/comments/{id}`。

---

## 10. 测试环境说明

| 项 | 值 |
|---|---|
| 被测地址 | `http://localhost:8081`（pytest 默认 `DEFAULT_BASE_URL`，可用 `--base-url` 或 `MIQU_BASE_URL` 覆盖） |
| 数据库 | MySQL，库名 `miqu`；初始化：`database/schema.sql` → `database/data.sql` |
| 测试账号 | 密码统一 `123456`：`admin`(管理员, id=1)、`test001`(id=2，主测试账号)、`test002`(id=3，与 test001 互关)、`banned001`(id=12，禁用)、`deleted001`(id=13，注销) |
| 后端启动 | `cd backend && mvn spring-boot:run`（默认 8081） |
| 前端启动 | `cd frontend && npm run dev`（5173，`/api` 代理到 8081） |
| 接口测试 | `pip install -r tests/requirements.txt && cd tests && python -m pytest` |
| 冒烟 | `python -m pytest -m smoke` |
| 只读 | `python -m pytest -m read`（不改数据） |

### 种子数据中可稳定断言的事实

| 事实 | 值 |
|---|---|
| `post` id=1 的点赞 / 评论 / 图片数 | 10 / 5 / 9（9 是需求上限） |
| `post` id=2 的图片数 | 0（边界：纯文字动态） |
| `post` id=3 的内容长度 | 1000 字符（边界：内容上限） |
| `user` id=2 ↔ id=3 | 互相关注（可用于私信正向用例） |
| `user` id=2 的粉丝数 | 14 |
| `conversation` id=4（user 4↔6） | **非互关历史会话**：可读、不可发（`NOT_MUTUAL_FOLLOW`） |
| `conversation` id=5（user 5↔7） | 同上 |

> 批量数据用确定性的 `INSERT ... SELECT` 生成（**不用随机数**），
> 因此每次初始化结果完全一致，可以断言数量。
>
> ⚠️ pytest 会真实落库（用户无法通过接口删除），库会随时间增长。
> JUnit 依赖种子绝对值，跑 `mvn test` 前建议先重置数据库。

---

## 11. API 与自动化测试的对应关系

现有自动化测试：**pytest 248 个用例**（13 个测试文件），
以及后端 **JUnit 291 个用例**（21 个测试类，`@Test` 实测统计）。

> 本节数字为 **2026-09-14 实测**。审计时（2026-09-12）pytest 为 201，
> 缺口（互关私聊专项、文件上传安全、并发）已于 2026-09-14 补齐；
> 同日 Bug Hunt 修复阶段再 +2（BUG-005 的 pytest 与 JUnit 回归各 2 条）。
>
> 另有 **12 项浏览器回归断言**（`tests/browser_regression.mjs`），
> 覆盖前端状态一致性——这部分**没有对应的接口缺陷**，故不出现在下面的接口映射里。

映射链：

```text
API 端点
   ↓
业务场景（正常 / 异常 / 边界 / 权限 / 状态迁移 / 数据一致性）
   ↓
pytest 测试函数（tests/api/test_*.py）
   ↓
（JUnit 侧：backend/src/test/java/com/miqu/**）
```

### 11.1 按模块的 pytest 覆盖

| 模块 | pytest 文件 | 用例数 | 覆盖端点 |
|---|---|---:|---|
| 认证 | `api/test_auth.py` | 19 | register、login、logout、`GET /api/users/me`（鉴权边界） |
| 用户 | `api/test_user.py` | 18 | me 系列、资料、密码、头像、白名单边界 |
| 关注 | `api/test_follow.py` | 19 | follow / unfollow、following / followers、主页关注状态 |
| 动态 | `api/test_post.py` | 31 | posts 全量 + 点赞 |
| 评论 | `api/test_comment.py` | 13 | 评论三接口 |
| 私信/会话 | `api/test_message.py`、**`api/test_message_mutual_follow.py`**、**`api/test_conversation_mutual_follow.py`** | 17 + **14** + **11** | `POST /api/messages`、会话列表/打开/记录/已读、未读数、**互关私聊规则（正负向）** |
| 文件 | **`api/test_file.py`** | **14** | `POST /api/files/image`（魔数/大小/MIME/空文件/可访问性） |
| 通知 | `api/test_notification.py` | 16 | 通知四接口 |
| 搜索 | `api/test_search.py` | 15 | `GET /api/users/search` |
| 管理后台 | `api/test_admin.py` | 53 | 后台 11 接口 + 举报提交/处理 |
| 并发 | **`api/test_concurrency.py`** | **6** | 注册/关注/取关/点赞/建会话/发消息 |

合计 **246**（与 `pytest --collect-only` 实测一致）。

### 11.2 无 pytest 断言的端点

实测分类（50 个端点）：

| 端点 | pytest | JUnit | 说明 |
|---|---|---|---|
| `GET /api/health` | ⚠️ 仅前置探测 | ✅ `HealthControllerTest` | pytest 只在 `conftest.ensure_backend_running` 里用它判断后端是否可跑，无结构断言 |
| `GET /api/posts/{id}/likes` | ❌ | ✅ `PostLikeControllerTest` | 从未被 pytest 调用 |
| `GET /api/users/{id}/following` | ❌ | ✅ `FollowControllerTest` | pytest 只测了 `/me/following` |
| `GET /api/users/{id}/followers` | ❌ | ✅ `FollowControllerTest` | pytest 只测了 `/me/followers` |
| `POST /api/reports` | ⚠️ 仅间接 | ✅ `ReportControllerTest` | pytest 只把它当后台场景的前置数据创建，未断言用户侧异常契约 |

即：**pytest 直接断言 45 个、间接 1 个、未覆盖 4 个**（这 4 个均有 JUnit 断言）。

> 历史对照：2026-09-12 审计时 `POST /api/files/image` 是**唯一零自动化覆盖**的接口，
> 现已被 `api/test_file.py`（14 条）覆盖。

### 11.3 后台访问控制矩阵的实际范围

`test_admin.py` 的 `ADMIN_ENDPOINTS` 常量只包含 **7 个 GET 接口**
（stats / users / users/{id} / posts / comments / reports / logs），
对每个接口断言 未登录 401、非管理员 403、管理员 200（7×3 = 21 个用例）。

管理后台的 **写接口**（`PUT /api/admin/users/{id}/status`、`DELETE /api/admin/posts/{id}`、
`DELETE /api/admin/comments/{id}`、`PUT /api/admin/reports/{id}/handle`）不在该矩阵中，
它们由各自的业务用例覆盖（含权限断言），但**没有**"未登录 401 / 非管理员 403"的矩阵式验证。

### 11.4 pytest 与 JUnit 的分工

| | JUnit + MockMvc | pytest |
|---|---|---|
| 运行方式 | 进程内，不起 HTTP | 真实 HTTP + 真实数据库 |
| 事务 | `@Transactional` 自动回滚 | 真实提交，不回滚 |
| 用例数 | 291 | 248 |
| 擅长 | 规则密集的分支覆盖、依赖种子绝对值 | 端到端联调、序列化契约、过滤器链 |

> 前端状态一致性（标签与数据是否同源）两套都测不到——后端返回永远是对的。
> 那一层由 `tests/browser_regression.mjs`（真实 Chrome，12 项断言）负责。

---

## 12. 已知与代码不一致之处（摘要）

完整清单与判定见 [`docs/testing/API_DOCUMENT_AUDIT.md`](../testing/API_DOCUMENT_AUDIT.md)。
其中前两条已于 **2026-09-14 修正**（下表中的状态列为准）：

| # | 审计发现（2026-09-12） | 2026-09-14 状态 |
|---|---|---|
| 1 | `README.md` 写"后端 38 个接口"，实际 50 | ✅ 已改为 50 |
| 2 | `README.md` / `tests/README.md` 写 pytest 229 / 实测 201；列举的 4 个测试文件不存在 | ✅ 4 个文件已补齐；pytest 实测 **246**，两份 README 已按实测更正 |
| 3 | `docs/design.md` 提到 `POST /api/admin/maintenance/rebuild-counts`，代码中不存在 | ⚠️ 未改动 `design.md`（原文是"建议提供"，属设计建议）；`README.md` 已改为指向 `docs/api/README.md` 作为接口清单权威来源 |
| 4 | `test_cases/` 目录不存在 | ✅ 已建立最小体系（`test_cases/`） |
| 5 | `docs/testing/` 缺 MIQU_TEST_SYSTEM / TEST_CASE_SCHEMA / TEST_COVERAGE_MATRIX / TEST_EXECUTION_REPORT，`docs/` 缺 TESTING_GUIDELINES | ✅ 均已补齐 |

> 本文件（`docs/api/README.md`）中的覆盖数据已同步到 2026-09-14；
> 审计报告 `API_DOCUMENT_AUDIT.md` 保留的是 **2026-09-12 的原始记录**，未回改，
> 以保证审计结论可追溯。
