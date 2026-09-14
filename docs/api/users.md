# 用户 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`UserController` / `UserServiceImpl` / `UpdateProfileRequest` / `ChangePasswordRequest` /
> `UpdateAvatarRequest` / `UserSearchQuery` / `UserVO` / `UserProfileVO` / `UserSearchVO`。

本模块 7 个接口。`/api/users/me/**` 与 `/api/users/search` 的鉴权边界见下（**注意 `/search` 是免登录的，`/me` 不是**）。
关注/取消关注与关注列表在 [follows.md](./follows.md)。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 获取当前登录用户信息 | GET | `/api/users/me` | 🔒 |
| 2 | 修改个人资料 | PUT | `/api/users/me` | 🔒 |
| 3 | 修改密码 | PUT | `/api/users/me/password` | 🔒 |
| 4 | 修改头像 | PUT | `/api/users/me/avatar` | 🔒 |
| 5 | 搜索用户 | GET | `/api/users/search` | — |
| 6 | 用户主页信息 | GET | `/api/users/{id}` | — |
| 7 | 某用户发布的动态 | GET | `/api/users/{id}/posts` | — |

> 路由优先级：`/me` 是字面路径，优先于 `/{id}` 模板，所以 `GET /api/users/me` 不会被 `GET /api/users/{id}` 抢走。

---

## 1. 获取当前登录用户信息

`GET /api/users/me`

| 项 | 值 |
|---|---|
| 认证 | **需要**（刻意不在白名单） |
| 成功业务码 | `200` |

### 请求参数

无。

### 请求示例

```http
GET /api/users/me HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `UserVO`（含邮箱等非公开字段，仅本人可见；**绝不含密码**）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
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
    "postCount": 3,
    "createTime": "2026-05-25 10:00:00"
  }
}
```

> 与后台的 `AdminUserVO` 相比，个人中心**不含 `status` 字段**（测试已断言）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token / Token 无效过期 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 携带已禁用账号的 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | Token 有效但账号已被逻辑删除 |

> **禁用立即生效**：认证过滤器每请求查库校验状态，管理员禁用后旧 Token 马上返回 423
> （而不是等 7 天过期）。

### 测试关注点

- 正向：`data` 无 `password`、`role==1`、无 `status` 字段、新用户三个计数均为 0。
- 鉴权：无 Token → 401；`/me`、`/me/following`、`/me/followers` 都必须要求登录（白名单用 `{id:[0-9]+}` 正是为了不放开 `/me`）。
- **禁用链路**：注册新用户 → 管理员 `PUT /api/admin/users/{id}/status` 置 `status=0` →
  该用户**同一个旧 Token** 再访问 `/me` 应立即 423，且不能重新登录（也 423）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_user.py::test_get_current_user` | 成功、无密码、无 status、计数为 0 |
| `tests/api/test_user.py::test_get_current_user_requires_login` | 401 |
| `tests/api/test_user.py::test_me_path_is_not_whitelisted` | `/me`、`/me/following`、`/me/followers` 均 401 |
| `tests/api/test_auth.py::test_me_requires_login` | 401 |
| `tests/api/test_auth.py::test_me_returns_403_after_account_disabled` | 禁用后旧 Token 423 + 无法重新登录（**用例名写 403，实际断言 423**，见审计报告） |

JUnit：`UserControllerTest`（18 用例）。

---

## 2. 修改个人资料

`PUT /api/users/me`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `nickname` | string | ✅ | `"新昵称"` | **`@NotBlank`，实际上必填**；≤32 字符；服务端 `trim()` |
| `gender` | integer | ❌ | `2` | `0/1/2`；越界 400；**null 表示不修改** |
| `birthday` | string(date) | ❌ | `"2000-01-01"` | `yyyy-MM-dd`；null 表示不修改 |
| `bio` | string | ❌ | `"新简介"` | ≤255 字符；null 表示不修改（要清空请传空串） |

**不可修改的字段**：`username`、`email`（有唯一约束，一期不支持改）、`role`、`status`、计数类字段。
请求里带上它们会被**静默忽略**（DTO 未定义这些字段）。

> ⚠️ **文档与实现不一致（已记入审计报告）**：Controller 的 Swagger 描述写
> "字段为 null 表示不修改"，但 `nickname` 上有 `@NotBlank`，
> 因此**不传 `nickname` 会返回 400 而不是"跳过"**。
> 实际契约是：`nickname` 必填，其余三个可选。

### 请求示例

```http
PUT /api/users/me HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "nickname": "改名后的昵称",
  "gender": 2,
  "birthday": "2000-01-01",
  "bio": "更新后的简介"
}
```

### 响应

**成功**：`data` 为更新后的 `UserVO`（字段同上文）。修改后用 `GET /api/users/me` 复查应看到新值。

**失败**：

```json
{ "code": 400, "message": "昵称不能为空", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `昵称不能为空` | `nickname` 缺失或为空串 |
| 400 | `PARAM_INVALID` | `昵称长度不能超过 32 个字符` | `nickname` 超长 |
| 400 | `PARAM_INVALID` | `性别取值只能是 0、1 或 2` | `gender` 越界（如 9） |
| 400 | `PARAM_INVALID` | `个人简介长度不能超过 255 个字符` | `bio` 超长 |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 非法 JSON |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 账号被禁用 |

### 测试关注点

- 正向：四个字段一起更新成功，且**再查一次确认已落库**。
- **越权尝试（重要）**：请求里塞入 `username`、`email`、`role: 2`，
  调用后重新查询必须发现三者**都没变**（尤其 `role` 仍为 1，不能被提权）。
- 参数：`nickname` 空串 / 33 字符；`gender=9`；`bio` 256 字符 → 均 400。
- 鉴权：未登录 → 401。
- 边界：只传 `nickname`（不传其余三个）应成功。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_user.py::test_update_profile` | 成功 + 落库复查 |
| `tests/api/test_user.py::test_update_profile_does_not_change_username_or_email` | 不能越权改用户名/邮箱/角色 |
| `tests/api/test_user.py::test_update_profile_validation` | 参数化 4 组：昵称空、昵称 33 字、gender=9、bio 256 字 → 400 |
| `tests/api/test_user.py::test_update_profile_requires_login` | 401 |

JUnit：`UserControllerTest`。

---

## 3. 修改密码

`PUT /api/users/me/password`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `oldPassword` | string | ✅ | `"123456"` | 非空；须与新密码哈希比对通过 |
| `newPassword` | string | ✅ | `"newpass123"` | 6~20 字符；不能与原密码相同 |

### 请求示例

```http
PUT /api/users/me/password HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "oldPassword": "123456", "newPassword": "newpass123" }
```

### 响应

**成功**：`data: null`。旧密码立即失效，新密码可登录。

```json
{ "code": 200, "message": "success", "data": null }
```

**失败**：

```json
{ "code": 400, "message": "原密码不正确", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `原密码不能为空` / `新密码不能为空` | 字段为空 |
| 400 | `PARAM_INVALID` | `新密码长度必须在 6~20 个字符之间` | 新密码长度不符 |
| 400 | `OLD_PASSWORD_MISMATCH` | `原密码不正确` | 原密码与库中哈希不匹配 |
| 400 | `PASSWORD_SAME_AS_OLD` | `新密码不能与原密码相同` | 新密码与原密码哈希一致 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 账号被禁用 |

> 校验顺序：先比对原密码（400 `原密码不正确`），再判断"新旧相同"（400 `新密码不能与原密码相同`）。
> 因此原密码填错时**不会**返回"新密码相同"。

### 测试关注点

- 正向：改密成功后，**旧密码登录 401、新密码登录 200**（两个方向都要断言）。
- 异常：原密码错误 → 400 + 精确文案；新旧相同 → 400 + 精确文案；新密码 3 位 → 400 且 message 含 `6~20`。
- 鉴权：未登录 → 401（本文件的 pytest 未单独覆盖，属缺口）。
- **安全**：日志只记录"发生了修改"，不记录新旧密码。
- **注意**：改密**不会让已签发的 Token 失效**（无状态 JWT），旧 Token 仍可用。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_user.py::test_change_password` | 成功 + 旧密码失效 + 新密码可用 |
| `tests/api/test_user.py::test_change_password_wrong_old` | 400 + `原密码不正确` |
| `tests/api/test_user.py::test_change_password_same_as_old` | 400 + `新密码不能与原密码相同` |
| `tests/api/test_user.py::test_change_password_too_short` | 400 + 含 `6~20` |

JUnit：`UserControllerTest`。

---

## 4. 修改头像

`PUT /api/users/me/avatar`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `avatar` | string | ✅ | `"/uploads/image/2026/09/x.jpg"` | 非空；≤255 字符；服务端 `trim()` |

> **两阶段模式**：先 `POST /api/files/image` 上传拿到 URL，再提交 URL。
> 注意：与"发布动态的图片"不同，**头像 URL 不做 `startsWith("/uploads/")` 前缀校验**，
> 因此传外链（如 `https://...`）也能成功——这是实现现状，不是文档遗漏。

### 请求示例

```http
PUT /api/users/me/avatar HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "avatar": "/uploads/image/2026/09/qa-avatar.jpg" }
```

### 响应

**成功**：`data` 为更新后的 `UserVO`，其中 `avatar` 为新值。

```json
{
  "code": 200,
  "message": "success",
  "data": { "id": "22", "avatar": "/uploads/image/2026/09/qa-avatar.jpg", "...": "其余 UserVO 字段" }
}
```

**失败**：

```json
{ "code": 400, "message": "头像地址不能为空", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `头像地址不能为空` | `avatar` 缺失或空串 |
| 400 | `PARAM_INVALID` | `头像地址长度不能超过 255 个字符` | `avatar` 超长 |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 非法 JSON |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |

### 测试关注点

- 正向：提交 `/uploads/...` 路径 → 200，响应 `avatar` 等于提交值。
- 异常：空串 → 400 + `头像地址不能为空`；超 255 字符 → 400。
- ~~外链应被拒~~：**当前实现不校验前缀**，不要写"外链 400"的断言（会失败）。
- 鉴权：未登录 → 401（pytest 未单独覆盖，属缺口）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_user.py::test_update_avatar` | 成功 + 返回值校验 |
| `tests/api/test_user.py::test_update_avatar_blank` | 400 + 精确文案 |

JUnit：`UserControllerTest`。

---

## 5. 搜索用户

`GET /api/users/search`

| 项 | 值 |
|---|---|
| 认证 | **不需要**（白名单）；登录后 `followedByMe` 才有意义 |
| 成功业务码 | `200` |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `keyword` | string | ✅ | — | `"张"` | **非空**；≤32 字符；同时匹配**昵称**与**用户名**（MySQL `LIKE`，未用 ES） |
| `page` | integer | ❌ | `1` | `1` | ≥1，越界 400 |
| `size` | integer | ❌ | `10` | `20` | 1~50，越界 400 |

**排序**：`followerCount DESC, id DESC`（粉丝多的排前面）。

**转义**：关键词中的 `%`、`_`、`\` 会被转义并配合 `ESCAPE '\\'`，
因此搜 `%` 只会命中真的含 `%` 的用户，而不会返回全部用户。

### 请求示例

```http
GET /api/users/search?keyword=test&page=1&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<UserSearchVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "2",
        "username": "test001",
        "nickname": "张三",
        "avatar": "https://i.pravatar.cc/150?img=12",
        "bio": "热爱摄影与旅行，喜欢记录生活的瞬间。",
        "followerCount": 14,
        "followedByMe": true
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20,
    "hasNext": false
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 用户 ID（字符串） |
| `username` / `nickname` / `avatar` / `bio` | string | 展示字段 |
| `followerCount` | number | 粉丝数 |
| `followedByMe` | boolean | 当前登录用户是否已关注；**游客恒为 `false`** |

> `UserSearchVO` **不含 email**（非公开信息）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `搜索关键词不能为空` | `keyword` 缺失、空串或纯空格（`trim` 后为空） |
| 400 | `PARAM_INVALID` | `搜索关键词不能超过 32 个字符` | `keyword` 超长 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` | `page=0` |
| 400 | `PARAM_INVALID` | `每页条数不能超过 50` | `size=51` |
| 400 | `PARAM_INVALID` | `缺少必要参数：keyword` | 完全不传 `keyword`（`MissingServletRequestParameterException`） |

> 缺少 `keyword` 时报的是 `缺少必要参数：keyword`，而不是"搜索关键词不能为空"——
> 因为它同时有 `@NotBlank`（`BindException` 路径）与"未传参数"（`MissingServletRequestParameterException` 路径），
> 实际触发哪条取决于是否把参数名传进来。**测试断言时注意区分**。

### 测试关注点

- 正向：按**昵称**匹配、按**用户名前缀**匹配；结果结构字段齐全。
- 排序：粉丝数倒序。
- **通配符转义（最容易被漏掉的边界）**：`keyword=%`、`keyword=_`、`keyword=%a%`、`keyword=\` 都不应返回全部用户，也不应报 500。
- 空结果：搜一个不存在的词 → `total=0`、`list=[]`、`hasNext=false`。
- 参数：空关键词 400；缺参数 400；33 字符 400。
- **游客视角**：免登录可调用，`followedByMe` 恒 `false`。
- **登录视角**：`followedByMe` 必须反映真实关注关系。
- ⚠️ **已知健壮性坑**：若用"公共前缀 + 固定 `size`"搜索再按 `username` 取键，
  当库中累积了大量测试用户（同名前缀超过一页）时，目标用户会被挤出第一页，
  导致断言 `None`。用例应改为**按精确用户名单独查询**，
  `tests/api/test_search.py::test_followed_by_me_reflects_real_relation` 即为此问题的修复结果。

### 对应自动化测试

`tests/api/test_search.py`（15 用例）：
`test_search_by_nickname`、`test_search_by_username_prefix`、`test_search_result_shape`、
`test_search_results_ordered_by_follower_count`、`test_search_no_match`、
`test_percent_is_escaped`、`test_underscore_is_escaped`、`test_wrapped_wildcard_is_escaped`、
`test_backslash_does_not_break_query`、`test_blank_keyword`、`test_missing_keyword`、
`test_keyword_too_long`、`test_search_is_public`、`test_guest_follow_flags_are_false`、
`test_followed_by_me_reflects_real_relation`

JUnit：`UserSearchTest`（16 用例）。

---

## 6. 用户主页信息

`GET /api/users/{id}`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `2` | 用户 ID；非数字 → 400 `参数格式不正确：id` |

### 请求示例

```http
GET /api/users/2 HTTP/1.1
Host: localhost:8081
```

### 响应

`data` 为 `UserProfileVO`（**不含 email**，额外带关注关系）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "2",
    "username": "test001",
    "nickname": "张三",
    "gender": 1,
    "bio": "热爱摄影与旅行，喜欢记录生活的瞬间。",
    "avatar": "https://i.pravatar.cc/150?img=12",
    "followingCount": 6,
    "followerCount": 14,
    "postCount": 3,
    "followedByMe": false,
    "followingMe": false,
    "mutual": false,
    "createTime": "2026-05-25 10:00:00"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `followedByMe` | boolean | **我**是否关注了 TA |
| `followingMe` | boolean | **TA** 是否关注了**我** |
| `mutual` | boolean | 是否**互相关注** = `followedByMe && followingMe`（**私信的前置条件**） |

> 游客、或查看自己主页时，三个标志位**恒为 `false`**。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | `用户不存在` | 用户不存在**或已被逻辑删除**（`deleted=1`） |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 不是数字 |

> **被禁用的用户主页仍可浏览**（`status=0` 不影响可见性），只是无法与之互动。

### 测试关注点

- **互关判断（核心）**：`test001` 与 `test002` 在种子数据里互关 →
  用 `test001` 的 Token 查 `id=3` 应得到 `followedByMe=true`、`followingMe=true`、`mutual=true`。
- 游客视角：三个标志位全 `false`。
- 隐私：响应**不含 `email`**。
- 可见性：不存在的 id → 404；已注销账号（id=13）→ 404；**已禁用账号（id=12）→ still 200**。
- 数字 ID 路径对游客开放（`/api/users/2` 与 `/api/users/2/posts` 均 200）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_guest_sees_false_follow_flags` | 游客三个标志位 false |
| `tests/api/test_follow.py::test_mutual_follow_is_reported` | 互关 → `mutual=true` |
| `tests/api/test_follow.py::test_profile_hides_email` | 不含 email |
| `tests/api/test_follow.py::test_profile_not_found` | 不存在的 id → 404 |
| `tests/api/test_follow.py::test_deleted_user_profile_is_not_visible` | 注销账号 → 404 |
| `tests/api/test_follow.py::test_banned_user_profile_is_still_visible` | 禁用账号主页仍可见 |
| `tests/api/test_user.py::test_numeric_user_path_is_public` | 游客可访问 |
| `tests/api/test_user.py::test_seed_user_profile` | 种子绝对值（关注 6 / 粉丝 14 / 动态 3） |

JUnit：`UserControllerTest`、`FollowControllerTest`。

---

## 7. 某用户发布的动态

`GET /api/users/{id}/posts`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | — | 用户 ID |
| `page` | Query | integer | ❌ | `1` | ≥1 |
| `size` | Query | integer | ❌ | `10` | 1~50 |

**排序**：`createTime DESC, id DESC`（时间倒序 + 主键兜底，避免同毫秒记录翻页错乱）。

### 请求示例

```http
GET /api/users/2/posts?page=1&size=10 HTTP/1.1
Host: localhost:8081
```

### 响应

`data` 为 `PageResult<PostVO>`（`PostVO` 字段说明见 [posts.md](./posts.md)）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "1",
        "content": "周末去了趟青海湖，天气好得不像话。...",
        "images": ["https://picsum.photos/seed/miqu1_0/800/600"],
        "likeCount": 10,
        "commentCount": 5,
        "likedByMe": false,
        "mine": false,
        "author": { "id": "2", "username": "test001", "nickname": "张三", "avatar": "...", "bio": "..." },
        "createTime": "2026-08-13 10:00:00"
      }
    ],
    "total": 3,
    "page": 1,
    "size": 10,
    "hasNext": false
  }
}
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | `用户不存在` | 用户不存在或已被逻辑删除 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页参数越界 |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 测试关注点

- 正向：种子 `test001`（id=2）应有 3 条动态（`postCount=3` 一致）。
- 一致性：该接口的 `total` 应与 `GET /api/users/{id}` 的 `postCount` 一致。
- 游客视角：`likedByMe` 与 `mine` 恒 `false`。
- 删除后的动态不应再出现在列表里（逻辑删除）。
- 分页：倒序、`hasNext` 正确。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_user_posts_listing` | 列表内容与作者 |
| `tests/api/test_post.py::test_seed_user_has_posts` | 种子动态数 |
| `tests/api/test_user.py::test_numeric_user_path_is_public` | 游客 200 |

JUnit：`PostControllerTest`。
