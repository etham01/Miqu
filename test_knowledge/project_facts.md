# Miqu 项目事实模型

> 本文件回答：**这是个什么系统、怎么分层、数据怎么流、主要测试对象是什么。**
> AI 建立全局认知的起点。所有事实均来自源码与配置，来源已标注。
>
> 置信度标记：`CONFIRMED` = 源码/配置直接确认 ｜ `INFERRED` = 由代码逻辑推断 ｜ `UNKNOWN` = 无法确认

---

## 1. 项目类型

| 维度 | 判定 | 证据 |
|---|---|---|
| 架构形态 | **前后端分离 + REST API + 单体后端应用** | `backend/`（Spring Boot）与 `frontend/`（Vue 3 + Vite）独立构建；前端有 `dist/` 产物 |
| 后端形态 | 单体应用（非微服务），分层为 Controller → Service → Mapper → DB | `backend/src/main/java/com/miqu/` 包结构 |
| 接口风格 | REST，JSON，统一 `Result` 包装 | `common/Result.java`、`docs/api/API_CONVENTIONS.md` |
| 前端形态 | SPA（Vue 3 + TypeScript + Vite） | `frontend/package.json` |
| 业务领域 | 社交论坛（动态 / 关注 / 点赞 / 评论 / 私信 / 通知 / 举报 / 管理后台） | `docs/design.md`、Controller 清单 |
| 是否 AI 应用 | **否**。无 LLM Provider、无 Prompt、无向量库 | 全库无相关依赖 |
| 是否 SDK/Library | 否，是可部署应用 | — |
| 部署形态 | 本机运行（无 Docker、无 CI） | 未找到 `Dockerfile` / `docker-compose.yml` / `.github/workflows` / `Jenkinsfile` — `CONFIRMED`（扫描确认不存在） |

**测试视角的类型含义**：主测试对象是 **HTTP API 层**（50 个端点），
其次是**前端状态一致性**（已证明存在 pytest 打不到的缺陷），数据库层断言为辅。

---

## 2. 技术栈

### 2.1 后端

| 项 | 值 | 来源 |
|---|---|---|
| Java | 21 | `backend/pom.xml:21` |
| Spring Boot | 3.4.7 | `backend/pom.xml:10` |
| ORM | MyBatis-Plus 3.5.7 | `backend/pom.xml:23` |
| 数据库 | MySQL 9.4（本机 3306，库名 `miqu`） | `docs/testing/TEST_EXECUTION_REPORT.md:17` |
| 认证 | JWT（jjwt 0.12.6），HS256，有效期 604800 秒（7 天） | `backend/pom.xml:24`、`application.yml:104` |
| 密码 | BCrypt（`PasswordEncoderConfig`） | `config/PasswordEncoderConfig.java` |
| 授权 | 自研拦截器 `AuthInterceptor` + `@RequireAdmin` 注解 | `security/AuthInterceptor.java` |
| 文件存储 | **本地磁盘**（`LocalFileStorageService`），非对象存储 | `service/impl/LocalFileStorageService.java` |
| 消息队列 | **无** | 全库无 MQ 依赖 — `CONFIRMED` |
| 缓存 | **无 Redis / 无 Spring Cache**。仅 MyBatis-Plus 一级缓存（默认行为） | 无相关依赖与注解 — `CONFIRMED` |
| 服务端口 | **8081**（非默认 8080，因本机 8080 被 Windows 服务占用） | `tests/conftest.py:46-51` 默认 base_url |
| API 文档 | SpringDoc OpenAPI（`OpenApiConfig`） | `config/OpenApiConfig.java` |

### 2.2 前端

| 项 | 值 | 来源 |
|---|---|---|
| 框架 | Vue 3.5 + TypeScript 5.6 | `frontend/package.json` |
| 构建 | Vite 6 | `frontend/package.json` |
| 单元测试框架 | **无**（未引入 Vitest / Jest） | `frontend/package.json` 无 test 脚本 — `CONFIRMED` |
| 端到端 | Playwright（`channel: chrome`，headless），脚本为 `.mjs` | `tests/browser_regression.mjs:44` |

> **测试影响**：前端**没有组件级单元测试**，前端逻辑只能靠浏览器回归覆盖。
> 这是本项目已知的结构性缺口（见 `issues.yaml` M-03），也是 `browser_regression.mjs` 存在的原因。

### 2.3 测试栈

| 项 | 值 | 来源 |
|---|---|---|
| JUnit | 5（由 `spring-boot-starter-test` 管理版本） | `backend/pom.xml:96-99` |
| MockMvc | `@AutoConfigureMockMvc` | `backend/src/test/.../BaseControllerTest.java:7` |
| 断言库 | AssertJ + MockMvc `jsonPath` 两种并存 | `AuthControllerTest.java:9` |
| pytest | ≥8.0（实测 9.1.1） | `tests/requirements.txt:2` |
| requests | ≥2.31（实测 2.34.2） | `tests/requirements.txt:3` |
| Python | 3.12.10 | `TEST_EXECUTION_REPORT.md:18` |
| Maven | 3.9.14 ｜ Node 22.22.2 | `TEST_EXECUTION_REPORT.md:15,21` |
| H2 内存库 | **未使用**，JUnit 也连真实 MySQL | `pom.xml` 无 H2 依赖 — `CONFIRMED` |
| Testcontainers | **未使用** | 无依赖 — `CONFIRMED` |
| CI | **无** | 扫描确认 — `CONFIRMED` |

> **测试影响**：因为不用 H2、不用 Testcontainers，**两套测试都依赖一个真实运行的 MySQL 与种子数据**。
> 任何 AI 生成的测试都必须假设"数据库是真实的、会被写入的、没有自动清理"，
> 这直接决定了幂等纪律（见 `execution/pytest_contract.yaml`）。

### 2.4 外部依赖清单

| 依赖 | 类型 | 是否需 Mock | 备注 |
|---|---|---|---|
| MySQL | 数据库 | ❌ 用真实库 | 两套测试都打真实库 |
| 本地文件系统（`uploads/`） | 文件存储 | ❌ 用真实目录 | 上传测试会真实落盘 |
| `i.pravatar.cc` | **外部图床**（仅种子头像 URL 引用） | 无法 Mock | 离线时头像破图，**不影响功能**，已知非缺陷（`MIQU_TEST_SYSTEM.md` §9.3） |

**没有**：第三方 API、短信/邮件服务、支付、OAuth 外部登录、LLM 服务。
这意味着测试**不需要 Mock 外部服务**，这是一个重要的简化事实。

---

## 3. 系统架构与数据流

### 3.1 请求链路

```
浏览器（Vue 3 SPA）
    │  HTTP + JSON，Authorization: Bearer <JWT>
    ↓
[Servlet Filter 层]
    JwtAuthenticationFilter (OncePerRequestFilter)
      1. 解析 Authorization 头
      2. 校验 JWT 签名与过期 → 失败则设 request attribute AUTH_ERROR = UNAUTHORIZED
      3. selectById 查库 → 用户不存在则 UNAUTHORIZED；status != NORMAL 则 USER_DISABLED
      4. 成功 → CurrentUserHolder.set(LoginUser)（ThreadLocal）
      5. finally → CurrentUserHolder.clear()
    ↓
[Interceptor 层]
    AuthInterceptor (preHandle，注册于 /api/**)
      1. 非 HandlerMethod → 放行
      2. @RequireAdmin：currentUser == null → 401（或透传 authError）；!isAdmin → 403
      3. 不在免登录白名单 && currentUser == null → 401（或透传 authError）
      4. 其余放行
    ↓
[Controller 层]  16 个 Controller
      · Bean Validation（@Valid + @NotBlank/@Size/@Min/@Max/@Pattern/@Email）
      · CurrentUserArgumentResolver 注入当前用户 ID
    ↓
[Service 层]  12 个 ServiceImpl
      · requireActiveUser / requireVisibleUser（用户状态闸门）
      · 业务规则校验 → 抛 BizException(ErrorCode)
      · @Transactional 事务边界
      · 冗余计数 incr/decr、通知生成、管理日志记录
    ↓
[Mapper 层]  11 个 MyBatis-Plus Mapper
    ↓
MySQL（miqu 库，11 张表）

[异常出口]
    GlobalExceptionHandler (@RestControllerAdvice)
      · BizException        → Result.fail(code, message)
      · 校验/解析异常        → 400 + 具体文案
      · DuplicateKeyException → 按**索引名**翻译为业务 409（并发兜底的关键）
      · 其余                → 500
      · ⚠️ 所有 handler 均返回 HTTP 200，业务结果只在 body.code
```

### 3.2 静态资源链路（独立于上述 API 链路）

```
浏览器 → /uploads/** → WebMvcConfig 静态资源映射 → 本地 uploads/ 目录
                          ↑ 不经过 AuthInterceptor（不是 /api/**）
                          ↑ 文件缺失时返回**真正的 HTTP 404**（全项目唯一例外）
```

> **测试影响**：这是"HTTP 恒 200"约定的**唯一例外**，断言 `/uploads/**` 时必须用 `http_status`，
> 不能用 `body.code`。AI 极易在此出错，故单列。

### 3.3 关键架构事实（对测试设计有决定性影响）

| 事实 | 证据 | 测试含义 |
|---|---|---|
| **认证是"失败关闭"**：不在白名单的 `/api/**` 一律要求登录 | `AuthInterceptor` + `WebMvcConfig` | 新增端点默认需登录；未登录用例必须逐个白名单端点单独设计 |
| **Filter 只做身份识别，不做访问控制** | `JwtAuthenticationFilter` | 携带坏 Token 访问**白名单**接口时按游客放行，**不报错** |
| **JWT 每请求查库校验状态** | `JwtAuthenticationFilter.java:68-69` | 禁用账号的旧 Token **立即失效**（不必等 7 天过期）——这是 SEC-002 的实现基础 |
| **无物理外键**，引用完整性靠 Service + 索引 | `database/schema.sql` | 数据库层不会因外键报错；"目标不存在"必须由 Service 显式校验 |
| **关系表物理删除**（follow / post_like / post_image 无 `deleted` 列） | `schema.sql` + `BaseCreateEntity` | 取消关注/点赞后可立即重新建立（唯一键已释放）；不能用软删除假设 |
| **业务主表逻辑删除**（user / post / comment / message / notification 有 `deleted`） | `BaseEntity` + `@TableLogic` | 已删除数据对 API 不可见，但**仍在库里**；直接 SQL 断言必须带 `deleted = 0` |
| **冗余计数字段**（like_count / follower_count / post_count 等） | `schema.sql` | 计数与实际关系行数是**两条独立断言**，历史上出现过不一致 |
| **conversation 强制 `user1_id < user2_id`**（CHECK 约束 `ck_user_order`） | `schema.sql:157` | 同一对用户只有**一个**会话；构造数据时顺序写反会被 CHECK 拦下 |
| **会话并发创建用 `REQUIRES_NEW` 重查** | `ConversationServiceImpl.java:49-55,189` | 并发首建会话应恰好成功 1 次，其余复用而非 409（历史 Bug 已修） |

---

## 4. 功能模块

按 Controller 划分，共 10 个业务模块 / 16 个 Controller / 50 个端点。

| 模块 | Controller | 端点数 | 核心实体 | 涉及规则域前缀 |
|---|---|---|---|---|
| 认证 | `AuthController` | 3 | user | `AUTH` |
| 用户 | `UserController` | 13 | user, follow | `USER`, `FOL`, `SRCH`(搜索) |
| 动态 | `PostController` | 7 | post, post_image, post_like | `POST`, `LIKE` |
| 评论 | `CommentController` | 3 | comment | `CMT` |
| 会话 | `ConversationController` | 4 | conversation, message | `CONV` |
| 私信 | `MessageController` | 2 | message, conversation | `MSG` |
| 通知 | `NotificationController` | 4 | notification | `NOTI` |
| 举报 | `ReportController` | 1 | report | ⚠️ **无前缀**（见 `issues.yaml` C-05） |
| 文件 | `FileController` | 1 | —（落盘） | `FILE` |
| 系统 | `HealthController` | 1 | — | — |
| 管理后台 | `AdminUserController`、`AdminPostController`、`AdminCommentController`、`AdminReportController`、`AdminStatsController`、`AdminLogController` | 11 | 全部 | `SEC`（越权） |

> **端点数合计 50**，与 `docs/testing/MIQU_TEST_SYSTEM.md` §5 及
> `docs/testing/TEST_COVERAGE_MATRIX.md` 的口径一致（该数字在 2026-09-14 核对中由 38 更正为 50）。

### 模块间依赖（决定 workflow 与测试 setup 顺序）

```
认证 ──→ 用户 ──→ 关注 ──→ 互关判定 ──→ 会话/私信
                 ↓
              动态 ──→ 点赞 ──→ 通知
                 ↓
              评论 ──→ 通知
                 ↓
              举报 ──→ 管理后台处置 ──→ 删帖/删评/封号 ──→ 管理日志
```

**关键依赖事实**：私信/会话模块**强依赖互关状态**（`NOT_MUTUAL_FOLLOW` 403），
所以任何私信用例的 setup 必须先建立**双向**关注。这是本项目最高频的 setup 陷阱，
`execution/pytest_contract.yaml` 为此提供了 `_make_mutual()` 惯例。

---

## 5. 主要测试对象与优先级

| 优先级 | 测试对象 | 理由 | 对应知识文件 |
|---|---|---|---|
| **P0** | 互关私聊规则（MSG / CONV） | 项目**冻结规则**，历史上专门补过 25 条 pytest | `rules/business_rules.yaml` |
| **P0** | 越权与认证边界（SEC） | 安全边界，`@RequireAdmin` + 白名单 + 资源所有者三重判定 | `permissions/permission_matrix.yaml` |
| **P0** | 并发竞态（CONC） | 唯一键兜底 + `REQUIRES_NEW`，历史上真出过 409 Bug | `rules/side_effects.yaml` |
| **P0** | 上传安全（FILE） | 魔数校验、路径穿越、大小限制 | `data/test_data.yaml` |
| **P1** | 举报处置状态机 | 多分支校验顺序复杂，`action` 与 `targetType` 必须匹配 | `states/state_machines.yaml` |
| **P1** | 冗余计数一致性 | like_count / follower_count 等与实际行数是否一致 | `rules/side_effects.yaml` |
| **P1** | 前端状态一致性 | 已证明存在"后端全对、前端没发请求"的缺陷（BUG-002/003/004） | `execution/browser_contract.yaml` |
| **P2** | 参数边界与格式校验 | 由 Bean Validation 统一处理，模式固定 | `data/test_data.yaml` |
| **P2** | 分页与查询组合 | 风险低，`page`/`size` 约束统一 | `api/endpoints.yaml` |

---

## 6. 测试环境事实（AI 生成可执行代码必须知道）

| 项 | 值 | 来源 |
|---|---|---|
| 后端 base_url | `http://localhost:8081`（可用 `--base-url` 或 `MIQU_BASE_URL` 覆盖） | `tests/conftest.py:46-51` |
| 数据库重置 | `mysql -u root -p < database/schema.sql` 然后 `< database/data.sql` | `MIQU_TEST_SYSTEM.md` §4.1 |
| JUnit 前置 | **必须先重置数据库**（依赖种子数据绝对值断言） | 同上 |
| pytest 前置 | **必须先启动后端**（打真实 HTTP）；未启动时 `conftest.py` 直接明确报错退出，不会刷一屏连接拒绝 | `conftest.py` 的 `ensure_backend_running` |
| 浏览器回归前置 | 必须先 `cd frontend && npm run dev` | `MIQU_TEST_SYSTEM.md` §4.4 |
| pytest 幂等性 | ✅ 连跑两次结果一致，中途**不需要**重置数据库（2026-09-16 实测 250 passed） | `MIQU_TEST_SYSTEM.md` §7 |
| 数据清理 | **项目没有删除用户的接口**；靠随机用户名保证不撞唯一键，跑完库里会多出测试用户，这是**预期行为** | `tests/README.md:82` |

---

## 7. 本项目的三条测试硬纪律（AI 生成代码必须遵守）

摘自 `docs/testing/MIQU_TEST_SYSTEM.md` §6，是本项目的**红线**，不是建议：

1. **不许把测试改绿。** 禁止删除测试、`skip`、放宽断言、捕获异常后忽略、改测试数据掩盖 Bug。
   测试红了先查代码，不要先查测试。
2. **pytest 断言用差值，不用绝对值。** 写入真实落库且无删除接口，库里数据随运行次数增长。
   写 `assert 用户总数 == 21` 第二次跑就挂。
   （唯一例外：针对**种子数据**的绝对值断言，如"post 1 有 9 张图"。）
3. **写数据只用当次运行新建的随机用户**（`fresh_user` / `fresh_users` 夹具），不要改种子账号。

> 第 4 点（唯一的合法"红转绿"路径）：种子数据的绝对值断言在手工 curl / 前端操作后会失效，
> 正确处理是**重置数据库**，而不是改断言。

机器可读版本见 [`execution/pytest_contract.yaml`](execution/pytest_contract.yaml) 的 `hard_rules` 段。
