# 认证 API

> 通用约定（响应信封、错误码、序列化）见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`AuthController` / `AuthServiceImpl` / `RegisterRequest` / `LoginRequest` / `LoginVO` / `UserVO`。

本模块 3 个接口。**`/api/auth/register` 与 `/api/auth/login` 免登录**，`/api/auth/logout` 需要登录。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 用户注册 | POST | `/api/auth/register` | — |
| 2 | 用户登录 | POST | `/api/auth/login` | — |
| 3 | 退出登录 | POST | `/api/auth/logout` | 🔒 |

---

## 1. 用户注册

`POST /api/auth/register`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `username` | string | ✅ | `"qa3f9c1a2b"` | 4~20 字符；必须以**字母开头**，仅可含字母/数字/下划线（`^[A-Za-z][A-Za-z0-9_]*$`）；服务端会 `trim()` |
| `password` | string | ✅ | `"123456"` | 6~20 字符；以 BCrypt 哈希存储，**任何响应都不返回密码** |
| `nickname` | string | ✅ | `"张三"` | ≤32 字符；服务端会 `trim()` |
| `email` | string | ✅ | `"zhangsan@miqu.com"` | ≤64 字符；需合法邮箱格式；服务端 **`trim()` + 转小写**（大小写视为同一邮箱） |
| `gender` | integer | ❌ | `1` | `0` 未知 / `1` 男 / `2` 女；**不传默认 0**；越界 400 |
| `birthday` | string(date) | ❌ | `"1998-03-15"` | `yyyy-MM-dd` |
| `bio` | string | ❌ | `"热爱摄影"` | ≤255 字符；不传存空串 |
| `avatar` | string | ❌ | `"/uploads/image/2026/09/a.jpg"` | ≤255 字符；不传存空串 |

> `role` 与 `status` **不可由调用方指定**：服务端强制 `role=1`（普通用户）、`status=1`（正常），
> 计数类字段初始化为 0。

### 请求示例

```http
POST /api/auth/register HTTP/1.1
Host: localhost:8081
Content-Type: application/json

{
  "username": "qa3f9c1a2b",
  "password": "123456",
  "nickname": "测试用户1a2b",
  "email": "qa3f9c1a2b@miqu.test",
  "gender": 1,
  "birthday": "1998-03-15",
  "bio": "接口测试账号"
}
```

### 响应

**成功**（`code=200`，`data` 为 `UserVO`）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "22",
    "username": "qa3f9c1a2b",
    "nickname": "测试用户1a2b",
    "email": "qa3f9c1a2b@miqu.test",
    "gender": 1,
    "birthday": "1998-03-15",
    "bio": "接口测试账号",
    "avatar": "",
    "role": 1,
    "followingCount": 0,
    "followerCount": 0,
    "postCount": 0,
    "createTime": "2026-09-12 14:30:00"
  }
}
```

`UserVO` 字段：`id`(**字符串**)、`username`、`nickname`、`email`、`gender`、`birthday`、`bio`、`avatar`、`role`、`followingCount`、`followerCount`、`postCount`、`createTime`。

**失败**（用户名重复）：

```json
{ "code": 409, "message": "用户名已被占用", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `用户名长度必须在 4~20 个字符之间` | 用户名长度不符 |
| 400 | `PARAM_INVALID` | `用户名必须以字母开头，且只能包含字母、数字和下划线` | 用户名格式不符（如 `1abcdef`） |
| 400 | `PARAM_INVALID` | `密码长度必须在 6~20 个字符之间` | 密码长度不符 |
| 400 | `PARAM_INVALID` | `邮箱格式不正确` | 邮箱格式非法 |
| 400 | `PARAM_INVALID` | `昵称不能为空` / `昵称长度不能超过 32 个字符` | 昵称为空或超长 |
| 400 | `PARAM_INVALID` | `邮箱不能为空` / `邮箱长度不能超过 64 个字符` | 邮箱为空或超长 |
| 400 | `PARAM_INVALID` | `用户名不能为空` / `密码不能为空` | 必填字段为空（**注意**：空值会同时违反 `@NotBlank`/`@Size`/`@Pattern`，文案不保证是哪一条，只断言 code 更稳） |
| 400 | `PARAM_INVALID` | `性别取值只能是 0、1 或 2` | `gender` 不在 0~2 |
| 400 | `PARAM_INVALID` | `个人简介长度不能超过 255 个字符` | `bio` 超长 |
| 400 | `PARAM_INVALID` | `头像地址长度不能超过 255 个字符` | `avatar` 超长 |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 请求体不是合法 JSON |
| 409 | `USERNAME_EXISTS` | `用户名已被占用` | 用户名已存在（并发下由 `uk_username` 兜底映射） |
| 409 | `EMAIL_EXISTS` | `邮箱已被注册` | 邮箱已存在（大小写归一化后比较；并发下由 `uk_email` 兜底） |

### 测试关注点

- **正向**：最小必填字段注册成功；带全字段注册成功。
- **安全**：响应中**绝不能出现 `password`**；新用户 `role` 必须为 `1`（不能自封管理员）。
- **唯一性**：重复用户名 → 409 `用户名已被占用`；重复邮箱 → 409；**邮箱大小写不同也应判定为重复**（`QA@X.com` vs `qa@x.com`）。
- **参数**：用户名长度边界 3/4/20/21；用户名以数字开头；密码 5/6/20/21；邮箱缺少 `@` 或域名。
- **空白处理**：`username` 前后带空格应当被 `trim` 后注册（若已存在则 409）；纯空格 `username` → 400。
- **不传 `gender`**：应默认 `0`，不是 400。
- **响应类型**：`data.id` 是**字符串**、计数字段是**数字**。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_auth.py::test_register_success` | 成功、无密码字段、`role==1` |
| `tests/api/test_auth.py::test_register_duplicate_username` | 409 与精确文案 |
| `tests/api/test_auth.py::test_register_duplicate_email_is_case_insensitive` | 邮箱大小写归一化 → 409 |
| `tests/api/test_auth.py::test_register_parameter_validation` | 参数化：仅违反一条约束的 4 组输入 + 精确文案 |
| `tests/api/test_auth.py::test_register_blank_username_only_checks_code` | 空用户名：同时违反多约束 → 只断言 code |
| `tests/api/test_admin.py::test_stats_user_total_increments_after_register` | 注册后后台统计 `userTotal` +1 |

JUnit：`AuthControllerTest`（19 用例）、`SeedPasswordTest`（2 用例）。

---

## 2. 用户登录

`POST /api/auth/login`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `username` | string | ✅ | `"test001"` | 非空；服务端 `trim()` |
| `password` | string | ✅ | `"123456"` | 非空（不做长度校验，仅比对哈希） |

### 请求示例

```http
POST /api/auth/login HTTP/1.1
Host: localhost:8081
Content-Type: application/json

{ "username": "test001", "password": "123456" }
```

### 响应

**成功**（`code=200`，`data` 为 `LoginVO`）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "user": {
      "id": "2",
      "username": "test001",
      "nickname": "张三",
      "email": "test001@miqu.com",
      "gender": 1,
      "birthday": "1998-03-15",
      "bio": "热爱摄影与旅行，喜欢记录生活的瞬间。",
      "avatar": "https://i.pravatar.cc/150?img=12",
      "role": 1,
      "followingCount": 6,
      "followerCount": 14,
      "postCount": 1,
      "createTime": "2026-05-25 10:00:00"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `token` | string | JWT，后续放入 `Authorization: Bearer {token}` |
| `tokenType` | string | 固定 `"Bearer"` |
| `expiresIn` | number | 有效期秒数，固定 `604800` |
| `user` | object | `UserVO`，登录即返回，前端无需再调 `GET /api/users/me` |

**失败**：

```json
{ "code": 401, "message": "用户名或密码错误", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `用户名不能为空` / `密码不能为空` | 字段为空 |
| 401 | `INVALID_CREDENTIALS` | `用户名或密码错误` | **用户不存在** 或 **密码错误**（两者返回完全相同的响应） |
| 401 | `INVALID_CREDENTIALS` | `用户名或密码错误` | 账号已被逻辑删除（`deleted=1`）→ 查不到用户 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | `status=0` |

> **设计要点**：先校验密码、再看状态，且"用户不存在"与"密码错误"返回**逐字相同**的响应，
> 防止攻击者用响应差异枚举有效用户名。

### 测试关注点

- **正向**：种子账号 `test001/123456` 登录成功；断言 `token` 非空、`tokenType=="Bearer"`、`expiresIn>0`。
- **认证失败**：密码错误 → 401；**不存在的用户 → 401 且 `message` 与密码错误完全相同**（防枚举，必须逐字比对）。
- **禁用账号**：`banned001/123456` → 423，`message` 含"禁用"。
- **注销账号**：`deleted001/123456` → 401（逻辑删除后表现为"用户不存在"）。
- **参数**：`username`/`password` 为空 → 400。
- **响应安全**：`data.user` 中**不得出现 `password`**。
- **重复登录**：同一账号可多次登录并各自拿到有效 Token（无状态，不受限制）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_auth.py::test_login_success` | 成功、`tokenType`、`expiresIn`、无密码字段 |
| `tests/api/test_auth.py::test_login_wrong_password` | 401 + 精确文案 |
| `tests/api/test_auth.py::test_login_unknown_user_returns_same_message` | 用户不存在与密码错误文案**完全一致** |
| `tests/api/test_auth.py::test_login_disabled_user` | 423 |
| `tests/api/test_auth.py::test_login_deleted_user` | 401 |
| `tests/api/test_auth.py::test_login_blank_parameters` | 参数化：用户名空 / 密码空 → 400 |

JUnit：`AuthControllerTest`。

---

## 3. 退出登录

`POST /api/auth/logout`

| 项 | 值 |
|---|---|
| 认证 | **需要**（不在白名单） |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数

无（仅需 `Authorization` 请求头）。

### 请求示例

```http
POST /api/auth/logout HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

**成功**：

```json
{ "code": 200, "message": "success", "data": null }
```

**失败**：

```json
{ "code": 401, "message": "未登录或登录状态已失效", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 未带 Token 或 Token 无效/过期 |

### 测试关注点

- **正向**：带有效 Token 调用 → 200。
- **鉴权**：无 Token → 401（该接口**不在白名单**，容易误以为可以免登录调用）。
- **⚠️ 已知限制（重要，别误判为 Bug）**：JWT 无状态，服务端不维护黑名单，
  **退出后旧 Token 在 7 天内仍然可用**。该接口只写审计日志（服务端 `log.info`）。
  这是设计取舍（见 `README.md`「设计偏离说明 #7」），不是缺陷。
- 因此"退出后不能再访问受保护接口"这类断言**在本架构下不成立**，不要这样写用例。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_auth.py::test_logout_requires_login` | 无 Token → 401 |
| `tests/api/test_auth.py::test_logout_with_token` | 带 Token → 200 |

> 当前**没有**用例断言"退出后旧 Token 失效"——因为该行为在本架构下不成立（见上）。
