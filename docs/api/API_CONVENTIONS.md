# Miqu API 通用约定

> **本文档的唯一事实来源是当前生产代码**（`backend/src/main/java`）。
> 生成方式为反向整理（Controller / DTO / VO / Service / ErrorCode / 拦截器 / 过滤器），
> 不是设计稿。凡是与代码不一致的地方，以代码为准，并记录在
> [`docs/testing/API_DOCUMENT_AUDIT.md`](../testing/API_DOCUMENT_AUDIT.md)。

---

## 1. Base URL 与端口

| 项 | 值 |
|---|---|
| 默认端口 | `8081`（`server.port: ${SERVER_PORT:8081}`） |
| 本地 Base URL | `http://localhost:8081` |
| 上下文路径 | **无**（未配置 `server.servlet.context-path`） |
| 前端开发代理 | Vite `5173` 把 `/api` 代理到 `8081` |
| OpenAPI 文档 | `GET /v3/api-docs`、`GET /swagger-ui.html`（**免登录**，不在 `/api/**` 拦截范围内） |

> 8080 不使用的原因见 `application.yml` 注释：本机 8080 被 Windows 服务长期占用。
> 换机器可用环境变量覆盖：`SERVER_PORT=8080`。

---

## 2. 认证方式（JWT / Bearer）

- 登录成功后返回 `token`，后续请求放入请求头：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....
```

- 算法 **HS256**；`issuer=miqu`；有效期 **604800 秒（7 天）**，登录响应的 `expiresIn` 即此值。
- 载荷包含 `sub`(userId)、`username`、`role`。
- 服务端**无状态**：不存会话、不存黑名单。`POST /api/auth/logout` 只写审计日志，
  **不做 Token 失效**（已签发 Token 在 7 天内仍然可用）。
- 认证过滤器 `JwtAuthenticationFilter` 解析 Token 后**每次请求都会查库校验账号状态**：
  - 账号被逻辑删除（`deleted=1`）→ 视为未登录（`401`）
  - 账号被禁用（`status=0`）→ `423 USER_DISABLED`
  - 因此**管理员禁用用户会立即生效**，旧 Token 马上失效，不必等 7 天。
- 携带坏 Token 访问**白名单**接口时按游客放行（不报错），见 `AuthInterceptor` 注释。

### 2.1 免登录白名单（`miqu.auth.white-list`）

策略是**失败关闭**：不在此列表中的 `/api/**` **一律要求登录**。
格式 `METHODS:/ant/path`，METHODS 可省略表示任意方法。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/health` | 健康检查 |
| GET | `/api/posts` | 动态流（注意：`tab=following` 需登录，由 Service 拦成 401） |
| GET | `/api/posts/{id:[0-9]+}` | 动态详情 |
| GET | `/api/posts/{id:[0-9]+}/likes` | 点赞用户列表 |
| GET | `/api/posts/{id:[0-9]+}/comments` | 评论列表 |
| GET | `/api/users/search` | 搜索用户 |
| GET | `/api/users/{id:[0-9]+}` | 用户主页 |
| GET | `/api/users/{id:[0-9]+}/posts` | 某用户动态 |
| GET | `/api/users/{id:[0-9]+}/following` | 某用户关注的人 |
| GET | `/api/users/{id:[0-9]+}/followers` | 某用户粉丝 |

> `/api/users/me` **刻意不在白名单**（用 `{id:[0-9]+}` 而不是 `*`），
> 否则个人中心会变成免登录可访问。`/api/files/**` 同样要求登录。

### 2.2 管理员权限（两层）

`/api/admin/**` 全部标注 `@RequireAdmin`，失败原因可区分：

| 场景 | 业务码 | message |
|---|---|---|
| 未登录 | `401` | `未登录或登录状态已失效` |
| 已登录但非管理员 | `403` | `无权限执行该操作` |

---

## 3. 通用响应格式

所有接口（含失败）返回同一个信封：

```json
{ "code": 200, "message": "success", "data": {} }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | **业务状态码**，与 HTTP 语义对齐（200/400/401/403/404/409/423/500）。判断成功失败请看它 |
| `message` | string | 提示信息；成功固定 `success`，失败为面向调用方的中文文案 |
| `data` | object \| array \| null | 业务数据；失败时多为 `null` |

> ⚠️ **给测试人员的关键提醒：HTTP 状态码几乎恒为 `200`。**
> 业务失败（参数校验、未登录、无权限、不存在、冲突）都由
> `GlobalExceptionHandler`（`@RestControllerAdvice`）转成上面的信封返回，
> **没有 `@ResponseStatus`**，因此 HTTP 状态仍是 200。
>
> 唯一例外是健康检查在设计上仍返回 HTTP 200 + `code=500`（见 `system.md`）。
> **断言必须看 `code` 与 `message`，不要断言 HTTP 状态码。**

---

## 4. 业务错误码全集（`ErrorCode`）

### 4.1 通用

| code | 枚举名 | message |
|---|---|---|
| 200 | `SUCCESS` | `success` |
| 400 | `PARAM_INVALID` | `参数校验失败`（多数场景被更具体的文案覆盖） |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` |
| 403 | `FORBIDDEN` | `无权限执行该操作` |
| 404 | `NOT_FOUND` | `资源不存在` |
| 409 | `CONFLICT` | `资源冲突` |
| 500 | `INTERNAL_ERROR` | `系统异常，请稍后重试` |

### 4.2 认证 / 用户

| code | 枚举名 | message |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | `用户名或密码错误` |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` |
| 404 | `USER_NOT_FOUND` | `用户不存在` |
| 409 | `USERNAME_EXISTS` | `用户名已被占用` |
| 409 | `EMAIL_EXISTS` | `邮箱已被注册` |
| 400 | `OLD_PASSWORD_MISMATCH` | `原密码不正确` |
| 400 | `PASSWORD_SAME_AS_OLD` | `新密码不能与原密码相同` |
| 400 | `CANNOT_OPERATE_SELF` | `不能对自己执行该操作` |

### 4.3 关注

| code | 枚举名 | message |
|---|---|---|
| 400 | `CANNOT_FOLLOW_SELF` | `不能关注自己` |
| 409 | `ALREADY_FOLLOWED` | `已经关注过该用户` |
| 404 | `NOT_FOLLOWED` | `尚未关注该用户` |

### 4.4 动态

| code | 枚举名 | message |
|---|---|---|
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` |
| 400 | `EMPTY_POST` | `动态内容与图片不能同时为空` |
| 400 | `TOO_MANY_IMAGES` | `最多只能上传 9 张图片` |
| 400 | `INVALID_IMAGE_URL` | `图片地址不合法，请先通过上传接口获取` |
| 409 | `ALREADY_LIKED` | `已经点赞过该动态` |
| 404 | `NOT_LIKED` | `尚未点赞该动态` |

### 4.5 评论

| code | 枚举名 | message |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | `评论不存在或已被删除` |
| 400 | `EMPTY_COMMENT` | `评论内容不能为空` |

### 4.6 私信

| code | 枚举名 | message |
|---|---|---|
| 404 | `CONVERSATION_NOT_FOUND` | `会话不存在` |
| 403 | `NOT_CONVERSATION_MEMBER` | `你不是该会话的参与者` |
| 400 | `CANNOT_MESSAGE_SELF` | `不能给自己发送私信` |
| **403** | **`NOT_MUTUAL_FOLLOW`** | **`需要互相关注后才能私聊`** |

### 4.7 通知

| code | 枚举名 | message |
|---|---|---|
| 404 | `NOTIFICATION_NOT_FOUND` | `通知不存在` |

### 4.8 举报

| code | 枚举名 | message |
|---|---|---|
| 409 | `ALREADY_REPORTED` | `你已经举报过该内容` |
| 400 | `CANNOT_REPORT_SELF` | `不能举报自己` |
| 400 | `INVALID_TARGET_TYPE` | `举报目标类型不合法` |
| 409 | `REPORT_ALREADY_HANDLED` | `该举报已被处理` |
| 400 | `REPORT_ACTION_MISMATCH` | `处置动作与举报目标类型不匹配` |
| 400 | `REJECT_WITH_ACTION_NOT_ALLOWED` | `驳回举报时不能同时执行处置动作` |

### 4.9 管理后台

| code | 枚举名 | message |
|---|---|---|
| 403 | `CANNOT_DISABLE_ADMIN` | `不能禁用管理员账号` |
| 400 | `INVALID_REPORT_ACTION` | `不支持的处置动作` |

### 4.10 文件

| code | 枚举名 | message |
|---|---|---|
| 400 | `FILE_EMPTY` | `上传文件不能为空` |
| 400 | `FILE_TOO_LARGE` | `图片大小不能超过 5MB` |
| 400 | `FILE_TYPE_NOT_ALLOWED` | `仅支持 jpg / png / gif / webp 格式的图片` |

### 4.11 非枚举（框架层产生的码）

| code | message | 触发条件 |
|---|---|---|
| 404 | `请求的资源不存在` | 请求路径未匹配到任何接口（`NoResourceFoundException`） |
| 405 | `请求方法不支持：{METHOD}` | 路径存在但 HTTP 方法不对（`HttpRequestMethodNotSupportedException`） |
| 400 | `缺少必要参数：{name}` | 缺少必填 `@RequestParam`（如上传文件字段） |
| 400 | `参数格式不正确：{name}` | 路径/查询参数类型不匹配（如 `id=abc`） |
| 400 | `请求体格式不正确` | 请求体不是合法 JSON 或字段类型不匹配 |

### 4.12 唯一键冲突 → 业务码映射

并发下真正兜底的是数据库唯一键。`GlobalExceptionHandler` 按**索引名**翻译成业务码
（索引名与 `database/schema.sql` 一一对应）：

| 索引名 | 映射结果 |
|---|---|
| `uk_username` | 409 `USERNAME_EXISTS` |
| `uk_email` | 409 `EMAIL_EXISTS` |
| `uk_follower_following` | 409 `ALREADY_FOLLOWED` |
| `uk_post_user` | 409 `ALREADY_LIKED` |
| `uk_users` | 409 `CONFLICT`（未单独定义枚举） |
| `uk_reporter_target` | 409 `ALREADY_REPORTED` |
| 未登记约束 | 409 `CONFLICT` + 服务端 warn 日志 |

---

## 5. 参数校验行为

- JSON Body 校验失败 → `MethodArgumentNotValidException` → `code=400`，
  `message` 为**第一个字段错误的具体文案**（面向调用方的中文）。
- Query / 表单校验失败 → `BindException` 或 `ConstraintViolationException` → `code=400` + 具体文案。
- 校验器**不保证多约束的触发顺序**：同一字段同时违反多条时（如空用户名违反 `@NotBlank`+`@Size`+`@Pattern`），
  `message` 不唯一。**测试策略**：能构造"只违反一条约束"的输入时断言精确文案，否则只断言 `code`。

### 5.1 字段长度 / 数量上限（`BizConstants`）

| 常量 | 值 | 用于 |
|---|---|---|
| `USERNAME_MIN` / `USERNAME_MAX` | 4 / 20 | 用户名 |
| `USERNAME_PATTERN` | `^[A-Za-z][A-Za-z0-9_]*$` | 字母开头，仅字母/数字/下划线 |
| `PASSWORD_MIN` / `PASSWORD_MAX` | 6 / 20 | 密码（注册、改密码） |
| `NICKNAME_MAX` | 32 | 昵称 |
| `EMAIL_MAX` | 64 | 邮箱 |
| `EMAIL_PATTERN` | 常规邮箱正则 | 邮箱格式 |
| `BIO_MAX` | 255 | 个人简介 |
| `AVATAR_MAX` | 255 | 头像 URL |
| `POST_CONTENT_MAX` | 1000 | 动态内容 |
| `MAX_POST_IMAGES` | 9 | 动态图片数 |
| `COMMENT_CONTENT_MAX` | 500 | 评论内容 |
| `MESSAGE_CONTENT_MAX` | 1000 | 私信内容 |
| `MESSAGE_PREVIEW_LENGTH` | 100 | 会话列表消息预览截断 |
| `NOTIFICATION_SNAPSHOT_LENGTH` | 50 | 通知内容快照截断 |
| `REPORT_DETAIL_MAX` | 255 | 举报补充说明 |
| `DEFAULT_PAGE` / `DEFAULT_PAGE_SIZE` | 1 / 10 | 分页默认值 |
| `MAX_PAGE_SIZE` | 50 | 通用分页 size 上限 |
| `MAX_CHAT_PAGE_SIZE` | 50 | 聊天记录 size 上限（默认 20） |
| `MAX_IMAGE_SIZE` | 5MB | 上传图片大小上限 |

---

## 6. 分页约定

### 6.1 通用分页（`PageResult`）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 128,
    "page": 1,
    "size": 10,
    "hasNext": true
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `list` | array | 当前页数据（无数据时是 `[]`，不是 `null`） |
| `total` | number | 总记录数 |
| `page` | number | 当前页码，从 1 开始 |
| `size` | number | 每页条数 |
| `hasNext` | boolean | `page * size < total` |

- Query 参数：`page`（默认 1，`@Min(1)`）、`size`（默认 10，`@Min(1)`、`@Max(50)`）。
- **越界返回 400 而不是静默重置**：`page=0` → `页码必须大于 0`；`size=51` → `每页条数不能超过 50`。
- `total/page/size` 是**基本类型 long**，序列化为**数字**；`list[].id` 等包装类型 `Long` 序列化为**字符串**。

### 6.2 游标分页（聊天记录，仅 `GET /api/conversations/{id}/messages`）

不使用 offset，用 `beforeId` 游标：返回 `id < beforeId` 的最近 `size` 条，再按**时间正序**交付。

| 参数 | 默认 | 约束 |
|---|---|---|
| `beforeId` | 不传 | 首次加载不传；之后传上一页**最早一条**的 id。`@Min(1)` |
| `size` | 20 | `@Min(1)`、`@Max(50)` |

---

## 7. 序列化约定（前端与测试都依赖）

由 `JacksonConfig` 统一配置：

| 规则 | 示例 | 原因 |
|---|---|---|
| `id` 类字段（**包装类型 Long**）→ 字符串 | `{"id":"2"}` | JS Number 安全整数上限 2^53，防将来换雪花 ID 丢精度 |
| 计数类字段（**基本类型 long / Integer**）→ 数字 | `{"total":15}`、`{"likeCount":10}` | 要参与前端运算 |
| `LocalDateTime` → `yyyy-MM-dd HH:mm:ss` | `"2026-09-11 10:00:00"` | `spring.jackson.date-format` 对 java.time 无效，必须显式注册序列化器 |
| `LocalDate` → `yyyy-MM-dd` | `"1998-03-15"` | 生日 |
| 时区 | `Asia/Shanghai` | 与 JDBC `serverTimezone` 保持一致，否则前端会看到 8 小时偏差 |
| `null` 字段 | 默认输出（`default-property-inclusion: always`） | 例如 `"handler": null`、`"postId": null` |
| 未知字段 | 反序列化忽略（`fail-on-unknown-properties: false`） | 前端多传字段不会 400 |

> 踩坑记录：用 `Map<String, Long>` 返回计数时，自动装箱后的 `Long` 会被当成 id 一样转成字符串。
> 因此 `MessageUnreadVO.total` / `NotificationUnreadVO.*` / `AdminStatsVO.*` 一律用**基本类型 long** 声明。

---

## 8. 枚举字典（请求/响应中的魔法数字）

### 8.1 性别 `gender`（`GenderEnum`）

| 值 | 含义 |
|---|---|
| 0 | 未知（不传时的默认值） |
| 1 | 男 |
| 2 | 女 |

### 8.2 角色 `role`（`RoleEnum`）

| 值 | 含义 |
|---|---|
| 1 | 普通用户 |
| 2 | 管理员 |

### 8.3 用户状态 `status`（`UserStatusEnum`）

| 值 | 含义 |
|---|---|
| 0 | 已禁用 |
| 1 | 正常 |

### 8.4 通知类型 `type`（`NotificationTypeEnum`）

| 值 | 含义 |
|---|---|
| 1 | 关注 |
| 2 | 点赞 |
| 3 | 评论 |
| 4 | 私信 —— **保留值，一期不产生**（私信不写通知表） |

### 8.5 目标类型 `targetType`（`TargetTypeEnum`）

| 值 | 含义 | 可用于 |
|---|---|---|
| 1 | 用户 | 举报、操作日志 |
| 2 | 动态 | 举报、操作日志 |
| 3 | 评论 | 举报、操作日志 |
| 4 | 举报 | **仅操作日志**；提交举报时传 4 → `400 INVALID_TARGET_TYPE` |

### 8.6 举报处理状态 `status`（`ReportStatusEnum`）

| 值 | 含义 |
|---|---|
| 0 | 待处理 |
| 1 | 已处理（违规成立） |
| 2 | 已驳回（未违规） |

### 8.7 举报原因 `reasonType`（`ReportReasonEnum`）

| 值 | 含义 |
|---|---|
| 1 | 垃圾广告 |
| 2 | 辱骂骚扰 |
| 3 | 色情低俗 |
| 4 | 违法违规 |
| 5 | 其他 |

### 8.8 举报处置动作 `action`（`ReportActionEnum`）

| 值 | 含义 | 可用目标类型 |
|---|---|---|
| `NONE` | 不处置（不传时的默认值） | 任意 |
| `DELETE_POST` | 删除被举报动态 | 仅 `targetType=2` |
| `DELETE_COMMENT` | 删除被举报评论 | 仅 `targetType=3` |
| `DISABLE_USER` | 禁用被举报用户 | 仅 `targetType=1` |

### 8.9 管理员操作日志 `operationType`（`AdminLogService`）

`DISABLE_USER`、`ENABLE_USER`、`DELETE_POST`、`DELETE_COMMENT`、`HANDLE_REPORT`

### 8.10 动态流 `tab`（`PostQuery`）

| 值 | 含义 |
|---|---|
| `latest`（默认） | 全站最新动态，游客可访问 |
| `following` | 我关注的人的动态，**需登录**，否则 401 |

---

## 9. CORS

`WebMvcConfig.addCorsMappings`：

- 允许来源：`http://localhost:*`、`http://127.0.0.1:*`（用 `allowedOriginPatterns`，因为允许携带凭证时通配符 `*` 会被 Spring 拒绝）
- 允许方法：`GET, POST, PUT, DELETE, OPTIONS`
- 允许头：`*`；`allowCredentials=true`；预检缓存 3600 秒

> 生产环境需按实际域名调整，当前配置只覆盖本地开发。

---

## 10. 文件访问

上传的图片通过静态资源映射对外暴露（`WebMvcConfig.addResourceHandlers`）：

| 项 | 值 |
|---|---|
| URL 前缀 | `/uploads/**`（`miqu.upload.url-prefix`） |
| 物理目录 | `${UPLOAD_DIR:./uploads}` 的**绝对路径**（`miqu.upload.base-dir`） |
| 示例 | `GET /uploads/image/2026/09/abc123.jpg` |

> 用绝对路径注册是因为相对路径的解析基准是进程工作目录，从 IDE 和命令行启动可能落在不同位置，
> 会出现"上传成功却 404"。该静态路径**不在 `/api/**` 内**，因此免登录可访问。
