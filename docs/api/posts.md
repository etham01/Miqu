# 动态 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`PostController` / `PostServiceImpl` / `PostCreateRequest` / `PostQuery` /
> `PostVO` / `LikeResultVO`。

本模块 7 个接口。读接口（列表、详情、点赞列表）对游客开放；写接口一律要求登录。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 发布动态 | POST | `/api/posts` | 🔒 |
| 2 | 首页动态流 | GET | `/api/posts` | —（`tab=following` 需登录） |
| 3 | 动态详情 | GET | `/api/posts/{id}` | — |
| 4 | 删除动态 | DELETE | `/api/posts/{id}` | 🔒（作者或管理员） |
| 5 | 点赞用户列表 | GET | `/api/posts/{id}/likes` | — |
| 6 | 点赞 | POST | `/api/posts/{id}/like` | 🔒 |
| 7 | 取消点赞 | DELETE | `/api/posts/{id}/like` | 🔒 |

---

## 1. 发布动态

`POST /api/posts`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `content` | string | ❌ | `"周末去了青海湖"` | ≤1000 字符；服务端 `trim()`；null 视为空串 |
| `images` | string[] | ❌ | `["/uploads/image/2026/09/a.jpg"]` | ≤9 张；元素会 `trim` 并**过滤掉空串**；**每个 URL 必须以 `/uploads/` 开头** |

**跨字段规则（由 Service 校验，不是 Bean Validation）**：
`content` 与 `images` **至少要有一个非空**（"只传了空字符串"与"没传"等价）。

### 请求示例

```http
POST /api/posts HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "content": "周末去了趟青海湖，天气好得不像话。",
  "images": [
    "/uploads/image/2026/09/a1b2c3.jpg",
    "/uploads/image/2026/09/d4e5f6.jpg"
  ]
}
```

> 图片采用**两阶段模式**：先 `POST /api/files/image` 上传拿到 URL，再把 URL 列表提交。
> 上传与业务解耦，两边都好测。

### 响应

`data` 为 `PostVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "41",
    "content": "周末去了趟青海湖，天气好得不像话。",
    "images": ["/uploads/image/2026/09/a1b2c3.jpg", "/uploads/image/2026/09/d4e5f6.jpg"],
    "likeCount": 0,
    "commentCount": 0,
    "likedByMe": false,
    "mine": true,
    "author": {
      "id": "2",
      "username": "test001",
      "nickname": "张三",
      "avatar": "https://i.pravatar.cc/150?img=12",
      "bio": "热爱摄影与旅行，喜欢记录生活的瞬间。"
    },
    "createTime": "2026-09-12 14:30:00"
  }
}
```

`PostVO` 字段：`id`(字符串)、`content`、`images`(**有序** URL 数组，对应 `post_image.sort_order`)、
`likeCount`、`commentCount`、`likedByMe`、`mine`、`author`(`UserBriefVO`)、`createTime`。

**失败**：

```json
{ "code": 400, "message": "动态内容与图片不能同时为空", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `EMPTY_POST` | `动态内容与图片不能同时为空` | `content` 空且 `images` 空/全空串 |
| 400 | `PARAM_INVALID` | `动态内容不能超过 1000 个字符` | `content` 超长 |
| 400 | `PARAM_INVALID` | `最多只能上传 9 张图片` | `images` 长度 >9（Bean Validation 路径） |
| 400 | `TOO_MANY_IMAGES` | `最多只能上传 9 张图片` | 同上，Service 二次校验路径（文案相同、枚举不同） |
| 400 | `INVALID_IMAGE_URL` | `图片地址不合法，请先通过上传接口获取` | 任一 URL 不以 `/uploads/` 开头（**含外链**） |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 非法 JSON |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 账号已被逻辑删除 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 账号被禁用 |

### 副作用

插入 `post` → 按顺序插入 `post_image`（`sort_order` = 下标）→ `作者.post_count +1`。

### 测试关注点

- 正向：纯文字动态、带图动态（**返回的 `images` 顺序必须与提交顺序一致**）均成功。
- 边界：`content` 恰好 1000 字符 → **成功**；1001 → 400。
- 空内容：`content=""` 且无图 → 400 `动态内容与图片不能同时为空`；只有图无文字 → 成功。
- 图片：10 张 → 400；**外链（`https://...`）→ 400 `图片地址不合法`**；
  `images=[""]`（全空串）被过滤后等同于无图 → 与空内容组合时 400。
- 鉴权：未登录 → 401。
- 一致性：发布后 `user.postCount` +1，`GET /api/users/{id}/posts` 能查到。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_create_text_post` | 纯文字发布 |
| `tests/api/test_post.py::test_create_post_with_images_keeps_order` | 图片顺序保持 |
| `tests/api/test_post.py::test_create_post_content_and_images_both_empty` | 400 双空 |
| `tests/api/test_post.py::test_create_post_content_too_long` | 1001 字符 → 400 |
| `tests/api/test_post.py::test_create_post_content_exactly_max_length` | 1000 字符 → 成功（边界） |
| `tests/api/test_post.py::test_create_post_too_many_images` | 10 张 → 400 |
| `tests/api/test_post.py::test_create_post_rejects_external_image_url` | 外链被拒 |
| `tests/api/test_post.py::test_create_post_requires_login` | 401 |

JUnit：`PostControllerTest`（31 用例）。

---

## 2. 首页动态流

`GET /api/posts`

| 项 | 值 |
|---|---|
| 认证 | `tab=latest`（默认）不需要；**`tab=following` 需要登录** |
| 成功业务码 | `200` |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `tab` | string | ❌ | `latest` | `following` | **只能是 `latest` 或 `following`**；其他值 400 |
| `page` | integer | ❌ | `1` | `1` | ≥1 |
| `size` | integer | ❌ | `10` | `20` | 1~50 |

**排序**：

| tab | 返回 | 排序 |
|---|---|---|
| `latest` | 全站最新动态 | `createTime DESC, id DESC` |
| `following` | 我关注的人的动态（**不含自己的**） | 由 `PostMapper.selectFollowingFeed` 决定 |

> 带主键兜底（`id DESC`）是因为同一毫秒的记录在翻页时会重复或丢失。

### 请求示例

```http
GET /api/posts?tab=latest&page=1&size=20 HTTP/1.1
Host: localhost:8081
```

```http
GET /api/posts?tab=following&page=1&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<PostVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "40",
        "content": "这是一条测试动态 #40，用于验证首页列表、分页与计数逻辑。",
        "images": ["https://picsum.photos/seed/miqu40_0/800/600"],
        "likeCount": 3,
        "commentCount": 1,
        "likedByMe": false,
        "mine": false,
        "author": { "id": "13", "username": "deleted001", "nickname": "已注销用户", "avatar": "...", "bio": "..." },
        "createTime": "2026-09-11 23:00:00"
      }
    ],
    "total": 40,
    "page": 1,
    "size": 20,
    "hasNext": true
  }
}
```

> **游客访问时** `likedByMe` 与 `mine` **恒为 `false`**（不是报错）。
> 作者已被注销时 `author` 会被替换为 `UserBriefLoader.deletedPlaceholder()`。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | `tab=following` 但未登录（**由 Service 抛出**，不是拦截器） |
| 400 | `PARAM_INVALID` | `tab 取值只能是 latest 或 following` | `tab` 取值非法（如 `hot`） |
| 400 | `PARAM_INVALID` | `页码必须大于 0` | `page=0` |
| 400 | `PARAM_INVALID` | `每页条数不能超过 50` | `size=51` |

### 测试关注点

- 游客：`tab=latest` 200，且所有 `likedByMe`/`mine` 为 `false`。
- 排序：`latest` 按时间倒序（允许同毫秒，用 `createTime` 非递增断言）。
- 非法 tab（`hot`）→ 400。
- `size=51` → 400（**不是静默截断为 50**）。
- **`tab=following` 未登录 → 401**（路径在白名单里，但 Service 会拦）。
- **关注流隔离（核心）**：只有"我关注的人"的动态出现，**未关注者的不出现**，
  且**不含自己的动态**。
- 分页：`hasNext` 与实际数据一致；翻页不重不漏。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_list_latest_as_guest` | 游客可访问 |
| `tests/api/test_post.py::test_list_latest_guest_flags_are_false` | 游客标志位 false |
| `tests/api/test_post.py::test_list_latest_is_ordered_by_time_desc` | 倒序 |
| `tests/api/test_post.py::test_list_invalid_tab` | 非法 tab → 400 |
| `tests/api/test_post.py::test_list_size_over_limit` | size 越界 → 400 |
| `tests/api/test_post.py::test_following_tab_requires_login` | 未登录 → 401 |
| `tests/api/test_follow.py::test_following_feed_contains_only_followed_authors` | 关注流只含已关注者、不含自己 |

JUnit：`PostControllerTest`。

---

## 3. 动态详情

`GET /api/posts/{id}`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `1` | 动态 ID；非数字 → 400 |

### 请求示例

```http
GET /api/posts/1 HTTP/1.1
Host: localhost:8081
```

### 响应

`data` 为 `PostVO`（结构同"发布动态"的响应）。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "1",
    "content": "周末去了趟青海湖，天气好得不像话。整理了一组照片分享给大家 🌊",
    "images": [
      "https://picsum.photos/seed/miqu1_0/800/600",
      "https://picsum.photos/seed/miqu1_1/800/600"
    ],
    "likeCount": 10,
    "commentCount": 5,
    "likedByMe": false,
    "mine": false,
    "author": { "id": "2", "username": "test001", "nickname": "张三", "avatar": "...", "bio": "..." },
    "createTime": "2026-08-13 10:00:00"
  }
}
```

> **种子基准**：`post` id=1 → `likeCount=10`、`commentCount=5`、`images` 长度 9。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在**或已被逻辑删除**（含管理后台删除的） |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 测试关注点

- 正向：详情字段完整；图片列表**有序**（`sort_order` 升序）。
- 种子基准：`post 1` 的 10 赞 / 5 评论 / 9 图。
- 不存在（如 `99999999`）→ 404。
- **被删除后 → 404**（逻辑删除由 MyBatis-Plus 自动过滤，无需手写 `deleted=0`）。
- 游客访问 `likedByMe=false`、`mine=false`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_post_detail` | 详情字段 |
| `tests/api/test_post.py::test_post_detail_not_found` | 404 |
| `tests/api/test_post.py::test_post_images_are_ordered` | 图片有序 |

JUnit：`PostControllerTest`。

---

## 4. 删除动态

`DELETE /api/posts/{id}`

| 项 | 值 |
|---|---|
| 认证 | 需要（**作者本人或管理员**） |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `41` | 动态 ID |

### 请求示例

```http
DELETE /api/posts/41 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

**失败**：

```json
{ "code": 403, "message": "无权限执行该操作", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 403 | `FORBIDDEN` | `无权限执行该操作` | **既不是作者也不是管理员** |
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |

### 副作用（级联清理，四类关联数据策略不同）

| 关联数据 | 处理方式 | 原因 |
|---|---|---|
| `post` | **逻辑删除** | 保留审计痕迹 |
| `comment` | **逻辑删除** | 同上 |
| `post_image` | **物理删除** | 图片无保留价值，且 `uk_post_sort` 需要释放 |
| `post_like` | **物理删除** | 需释放 `uk_post_user` |
| `user.post_count` | `作者.post_count -1` | 计数回滚 |
| `notification` | **删除**该动态的点赞/评论通知 | 避免点进去是 404 |

> 与作者自删**走同一套 Service**，管理员删除额外写一条 `DELETE_POST` 操作日志。

### 测试关注点

- 作者自删 → 200；删除后详情 404、列表不再出现。
- **越权**：删除他人动态 → 403 + 精确文案。
- 管理员可删任意动态 → 200。
- 删除后**级联**：该动态的评论也应为 404；作者的 `postCount` -1。
- 重复删除 → 404。
- 未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_delete_own_post` | 作者删除 |
| `tests/api/test_post.py::test_delete_others_post_is_forbidden` | 403 |
| `tests/api/test_post.py::test_admin_can_delete_any_post` | 管理员删除 |
| `tests/api/test_post.py::test_delete_post_requires_login` | 401 |
| `tests/api/test_admin.py::test_stats_post_total_decreases_after_delete` | 统计随之下降 |

JUnit：`PostControllerTest`、`AdminContentControllerTest`。

---

## 5. 点赞用户列表

`GET /api/posts/{id}/likes`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | — | 动态 ID |
| `page` | Query | integer | ❌ | `1` | ≥1 |
| `size` | Query | integer | ❌ | `10` | 1~50 |

**排序**：`post_like.create_time DESC, id DESC`（最近点赞的在前）。

### 请求示例

```http
GET /api/posts/1/likes?page=1&size=10 HTTP/1.1
Host: localhost:8081
```

### 响应

`data` 为 `PageResult<UserBriefVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      { "id": "11", "username": "test010", "nickname": "刘一", "avatar": "https://i.pravatar.cc/150?img=33", "bio": "在读大学生，主修计算机。" }
    ],
    "total": 10,
    "page": 1,
    "size": 10,
    "hasNext": false
  }
}
```

`UserBriefVO`：`id`(字符串)、`username`、`nickname`、`avatar`、`bio`（**不含 email**）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 测试关注点

- 种子基准：`post` id=1 有 10 个点赞者 → `total == 10`。
- 游客可访问（白名单）。
- 动态不存在 → 404。
- 取消点赞后该用户从列表消失、`total` -1。
- 已注销用户的点赞项会回退为 `deletedPlaceholder`（不报错）。

> ⚠️ **自动化覆盖缺口**：本接口**没有任何 pytest 用例**（JUnit 侧有覆盖）。
> 详见 [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| — | **无 pytest 用例** |

JUnit：`PostLikeControllerTest`。

---

## 6. 点赞

`POST /api/posts/{id}/like`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `1` | 动态 ID |

### 请求示例

```http
POST /api/posts/1/like HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `LikeResultVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": { "liked": true, "likeCount": 11 }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `liked` | boolean | 操作后的点赞状态 |
| `likeCount` | number | **动态的最新点赞数**（重新读数，非缓存加减） |

**失败**：

```json
{ "code": 409, "message": "已经点赞过该动态", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 409 | `ALREADY_LIKED` | `已经点赞过该动态` | 已点赞（并发下由 `uk_post_user` 兜底为同一 409） |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 当前用户被禁用 |

### 副作用

插入 `post_like` → `post.like_count +1` → 给作者生成**点赞通知**（`type=2`）。
**给自己的动态点赞不产生通知**（由 `NotificationService.create` 统一拦截 `receiver==actor`）。

### 测试关注点

- 正向：点赞成功 → `liked=true`、`likeCount` 精确 +1。
- 重复点赞 → 409 + 精确文案。
- 取消后可再次点赞（来回切换计数一致）。
- 多用户点赞 → 计数累积正确。
- 未登录 → 401。
- 通知：他人点赞产生 1 条 `type=2` 通知；**自赞不通知**；取消点赞后通知被撤回。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_like_and_unlike` | 点赞 + 计数 |
| `tests/api/test_post.py::test_duplicate_like_returns_conflict` | 409 |
| `tests/api/test_post.py::test_like_again_after_unlike` | 取消后可再点赞 |
| `tests/api/test_post.py::test_like_count_accumulates_across_users` | 多用户累积 |
| `tests/api/test_post.py::test_like_requires_login` | 401 |
| `tests/api/test_post.py::test_liked_by_me_flag` | `likedByMe` 标志位 |
| `tests/api/test_notification.py::test_like_creates_notification` | 通知生成 |
| `tests/api/test_notification.py::test_unlike_removes_notification` | 通知撤回 |
| `tests/api/test_notification.py::test_self_actions_do_not_notify` | 自赞不通知 |

JUnit：`PostLikeControllerTest`（12 用例）。

---

## 7. 取消点赞

`DELETE /api/posts/{id}/like`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `1` | 动态 ID |

### 请求示例

```http
DELETE /api/posts/1/like HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": { "liked": false, "likeCount": 10 }
}
```

**失败**：

```json
{ "code": 404, "message": "尚未点赞该动态", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 404 | `NOT_LIKED` | `尚未点赞该动态` | 本来就没点赞（**返回 404 而不是静默 200**） |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

> **设计取舍**：与取消关注一致，取消一个不存在的关系返回 **404**。
> **连带要求**：前端点赞按钮必须在请求中禁用，否则连点两次会弹错误提示。
>
> **注意**：本接口**不校验当前用户活跃状态**（只 `requirePost`），
> 被禁用用户的 Token 已在校验过滤器处被拦成 423，因此实际不可达。

### 副作用

物理删除 `post_like` → `post.like_count -1` → **撤回**该点赞通知。

### 测试关注点

- 正向：取消成功 → `liked=false`、`likeCount` 精确 -1。
- 未点赞就取消 → 404 + 精确文案 `尚未点赞该动态`。
- 重复取消 → 第二次 404。
- 取消后可再次点赞。
- 未登录 → 401（pytest 未单独覆盖，属缺口）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_post.py::test_like_and_unlike` | 取消 + 计数 |
| `tests/api/test_post.py::test_unlike_without_like_returns_not_found` | 404 + 精确文案 |
| `tests/api/test_post.py::test_like_again_after_unlike` | 往返切换 |

JUnit：`PostLikeControllerTest`。
