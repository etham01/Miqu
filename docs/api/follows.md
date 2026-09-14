# 关注关系 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`UserController`（关注两个接口）/ `FollowServiceImpl` / `FollowStatusLoader` /
> `FollowResultVO` / `UserFollowVO`。

本模块 6 个接口。关注关系表 `follow` 采用**物理删除**（不是逻辑删除）——
否则唯一键会被软删行占用，导致"取消后永远无法再次关注/点赞"。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 关注用户 | POST | `/api/users/{id}/follow` | 🔒 |
| 2 | 取消关注 | DELETE | `/api/users/{id}/follow` | 🔒 |
| 3 | 我的关注列表 | GET | `/api/users/me/following` | 🔒 |
| 4 | 我的粉丝列表 | GET | `/api/users/me/followers` | 🔒 |
| 5 | 某用户关注的人 | GET | `/api/users/{id}/following` | — |
| 6 | 某用户的粉丝 | GET | `/api/users/{id}/followers` | — |

---

## 1. 关注用户

`POST /api/users/{id}/follow`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | `3` | **被关注者**的用户 ID |

### 请求示例

```http
POST /api/users/3/follow HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `FollowResultVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": { "following": true, "followerCount": 15 }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `following` | boolean | 操作后的关注状态，关注成功后为 `true` |
| `followerCount` | number | **被关注者的最新粉丝数**（服务端重新读数，非缓存加减） |

**失败**：

```json
{ "code": 409, "message": "已经关注过该用户", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `CANNOT_FOLLOW_SELF` | `不能关注自己` | `id` == 当前用户 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 目标用户不存在或已注销；**当前用户**不存在也走这条 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 目标用户**或自己**被禁用 |
| 409 | `ALREADY_FOLLOWED` | `已经关注过该用户` | 已关注（并发下由 `uk_follower_following` 兜底映射为同一 409） |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

> 校验顺序：`不能自己` → 自己活跃 → 目标活跃 → 是否已关注。
> 因此"关注被禁用的用户"返回 **423**，"关注不存在的用户"返回 **404**。

### 副作用

关注成功会在**同一事务**里做三件事：
1. 插入 `follow` 行；
2. `当前用户.following_count +1`、`目标用户.follower_count +1`；
3. 给目标用户生成一条**关注通知**（`type=1`）。

### 测试关注点

- 正向：关注成功 → `following=true`、`followerCount` 精确 +1（用差值断言）。
- 取消后重新关注：**必须能成功**（验证物理删除）。反复 关注/取消 3 次都应成功。
- 重复关注：第二次 → 409 + 精确文案 `已经关注过该用户`。
- 关注自己 → 400 + 精确文案 `不能关注自己`。
- 关注**禁用**用户（种子 `banned001`，id=12）→ 423。
- 关注**注销**用户（种子 `deleted001`，id=13）→ 404。
- 关注**不存在**用户（如 id=`99999999`）→ 404。
- 未登录 → 401。
- 通知联动：被关注者应新增 1 条关注通知；**自己关注自己不会产生通知**（且被 400 拦住）。
- **互关状态变化**：A 关注 B 后 `B 的主页`上 `followingMe=true`、`followedByMe=false`、`mutual=false`；
  B 回关后 `mutual=true` —— 这正是私信放行的条件。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_follow_and_unfollow` | 关注 + `followerCount` |
| `tests/api/test_follow.py::test_duplicate_follow_returns_conflict` | 409 + 精确文案 |
| `tests/api/test_follow.py::test_follow_again_after_unfollow` | 取消后可重新关注（物理删除） |
| `tests/api/test_follow.py::test_cannot_follow_self` | 400 + 精确文案 |
| `tests/api/test_follow.py::test_follow_disabled_user` | 423 |
| `tests/api/test_follow.py::test_follow_deleted_user` | 404 |
| `tests/api/test_follow.py::test_follow_unknown_user` | 404 |
| `tests/api/test_follow.py::test_follow_requires_login` | 401 |
| `tests/api/test_notification.py::test_follow_creates_notification` | 关注通知生成 |
| `tests/api/test_notification.py::test_self_actions_do_not_notify` | 自操作不通知 |

JUnit：`FollowControllerTest`（26 用例）。

---

## 2. 取消关注

`DELETE /api/users/{id}/follow`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | `3` | 被取消关注者的用户 ID |

### 请求示例

```http
DELETE /api/users/3/follow HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": { "following": false, "followerCount": 14 }
}
```

**失败**：

```json
{ "code": 404, "message": "尚未关注该用户", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 目标用户不存在或已注销 |
| 404 | `NOT_FOLLOWED` | `尚未关注该用户` | 本来就没关注（**返回 404 而不是静默 200**） |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

> **设计取舍**：取消一个不存在的关系返回 **404**（不是幂等的 200）。
> 语义明确、测试可断言。**连带要求**：前端关注按钮必须在请求中禁用，否则连点两次会弹错误提示。
>
> **注意**：本接口用 `requireVisibleUser` 而不是 `requireActiveUser`，
> 因此**可以取消关注一个已被禁用的用户**（不会返回 423）。
> 另外本接口**没有"不能取消关注自己"的校验**——传自己的 id 会因删不到行而返回
> `404 尚未关注该用户`。

### 副作用

取消成功会在同一事务里：物理删除 `follow` 行 → `我.following_count -1`、`对方.follower_count -1`
→ **撤回**"我关注对方"产生的关注通知。

> **对私信的影响（重点）**：取消关注会立即取消互关状态。
> 之后**历史会话仍可读、仍可标记已读**，但**不能再发送新消息、也不能重新打开会话**，
> 返回 `403 NOT_MUTUAL_FOLLOW`。详见 [messages.md](./messages.md) 与
> [conversations.md](./conversations.md)。

### 测试关注点

- 正向：取消成功 → `following=false`、`followerCount` 精确 -1。
- 未关注就取消 → 404 + 精确文案 `尚未关注该用户`。
- 取消后可重新关注（幂等往返 3 次）。
- 取消关注 → 对方**关注通知被撤回**（列表里不再出现该条）。
- **互关解除状态迁移（关键链路，不要拆散）**：
  `互关 → 发送消息成功 → 取消关注 → 历史消息仍可读 → 发送新消息 403 → 重新关注 → 发送恢复 200`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_follow_and_unfollow` | 取消 + `followerCount` |
| `tests/api/test_follow.py::test_unfollow_without_following` | 404 + 精确文案 |
| `tests/api/test_follow.py::test_follow_again_after_unfollow` | 往返 3 次 |
| `tests/api/test_notification.py::test_unfollow_removes_notification` | 通知撤回 |

JUnit：`FollowControllerTest`。

> **互关状态迁移的 pytest 覆盖现状**：`tests/api/test_message.py` 里的
> `_make_mutual()` 只负责"建立互关前置"，**没有**覆盖"解除互关后再发送被拒 / 重新互关后恢复"。
> 原先覆盖这些场景的 `test_message_mutual_follow.py`（10 用例）与
> `test_conversation_mutual_follow.py`（4 用例）**当前不在仓库中**（见审计报告）。
> JUnit 侧 `MessageControllerTest`、`FollowControllerTest` 含 mutual 相关前置。

---

## 3. 我的关注列表

`GET /api/users/me/following`

| 项 | 值 |
|---|---|
| 认证 | **需要**（不在白名单） |
| 成功业务码 | `200` |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | integer | ❌ | `1` | ≥1 |
| `size` | integer | ❌ | `10` | 1~50 |

**排序**：`follow.create_time DESC, follow.id DESC`（最近关注的在前）。

### 请求示例

```http
GET /api/users/me/following?page=1&size=50 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<UserFollowVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "3",
        "username": "test002",
        "nickname": "李四",
        "avatar": "https://i.pravatar.cc/150?img=13",
        "bio": "后端开发工程师，业余时间写写开源。",
        "followedByMe": true,
        "followTime": "2026-06-04 10:00:00"
      }
    ],
    "total": 6,
    "page": 1,
    "size": 50,
    "hasNext": false
  }
}
```

`UserFollowVO` 字段：`id`(字符串)、`username`、`nickname`、`avatar`、`bio`、`followedByMe`、`followTime`。

> 在"我的关注"列表里 `followedByMe` **恒为 `true`**。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | （当前用户自身）账号已被逻辑删除 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |

### 测试关注点

- 种子绝对值：`test001`（id=2）关注 6 人 → `total == 6`。
- 列表中每一项 `followedByMe` 均为 `true`。
- 取消关注后该用户从列表消失、`total` 减 1。
- 分页与排序（最新关注在前）。
- 鉴权：未登录 → 401（`/me/**` 不在白名单）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_following_and_follower_lists` | `total==6`、每项 `followedByMe=true` |
| `tests/api/test_user.py::test_me_path_is_not_whitelisted` | 未登录 401 |

JUnit：`FollowControllerTest`。

---

## 4. 我的粉丝列表

`GET /api/users/me/followers`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| 成功业务码 | `200` |

### 请求参数（Query）

同"我的关注列表"：`page`（默认 1）、`size`（默认 10，≤50）。

### 请求示例

```http
GET /api/users/me/followers?page=1&size=50 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<UserFollowVO>`，字段同"我的关注列表"。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "4",
        "username": "test003",
        "nickname": "王五",
        "avatar": "https://i.pravatar.cc/150?img=45",
        "bio": "设计师，喜欢一切有秩序的美。",
        "followedByMe": false,
        "followTime": "2026-06-30 10:00:00"
      }
    ],
    "total": 14,
    "page": 1,
    "size": 50,
    "hasNext": false
  }
}
```

> **本接口的 `followedByMe` 是"回关"标识**：`true` 表示"我"也关注了这个粉丝。
> 前端据此决定是否显示「回关」按钮，避免逐个再查一次。

### 错误情况

同"我的关注列表"（401 / 404 / 400）。

### 测试关注点

- 种子绝对值：`test001` 有 14 个粉丝 → `total == 14`。
- **回关标识（核心）**：14 个粉丝中，`test001` 回关了 6 个 →
  列表中 `followedByMe=true` 的应有 6 条，`false` 的 8 条。
- 鉴权：未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_follower_list_marks_followed_back` | `total==14`、回关 6 / 未回关 8 |

JUnit：`FollowControllerTest`。

---

## 5. 某用户关注的人

`GET /api/users/{id}/following`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | — | 目标用户 ID |
| `page` | Query | integer | ❌ | `1` | ≥1 |
| `size` | Query | integer | ❌ | `10` | 1~50 |

### 响应

`data` 为 `PageResult<UserFollowVO>`，结构同"我的关注列表"。

> **与"我的关注列表"的区别**：这里的 `followedByMe` 表示**当前登录用户**是否关注了列表中的每个人，
> 可能是 `false`。游客访问时 `followedByMe` 恒为 `false`。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | `用户不存在` | 目标用户不存在或已被逻辑删除 |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |

### 测试关注点

- 游客可访问（白名单）。
- 游客视角 `followedByMe` 恒 `false`；登录视角反映真实关系。
- 目标不存在/已注销 → 404；**目标被禁用仍可查询**（`requireVisibleUser`）。
- 分页与排序。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_following_and_follower_lists` | 列表内容与 `total` |
| `tests/api/test_follow.py::test_guest_sees_false_follow_flags` | 游客视角 |

JUnit：`FollowControllerTest`。

---

## 6. 某用户的粉丝

`GET /api/users/{id}/followers`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数

同"某用户关注的人`：`id`(Path) + `page` + `size`。

### 响应

`data` 为 `PageResult<UserFollowVO>`。

### 错误情况

同"某用户关注的人`（404 / 400）。

### 测试关注点

- 游客可访问。
- 种子断言：`test001`（id=2）的粉丝列表 `total == 14`。
- `followedByMe` 语义为"我是否回关了他"。
- 目标不存在 → 404。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_follow.py::test_follower_list_marks_followed_back` | `total==14`、回关标识 |

JUnit：`FollowControllerTest`。

---

## 附：互关的判定口径

**互关**严格定义为两条 `follow` 记录同时存在：

```text
互关(A, B) ≡ isFollowing(A, B) && isFollowing(B, A)
```

- 实现位置：`FollowStatusLoader.isMutual(a, b)`，**私信前置条件的唯一业务入口**。
- 只查关系表，**不额外判断用户是否活跃**（活跃性由调用方 `requireActiveUser` 负责）。
- 实时计算，**不落任何状态字段、不加缓存**。
- 管理员**不享有豁免**，同样需要互关。

`isMutual` 的返回 `false` 的情形包括：任一参数为 `null`、`a.equals(b)`（自己与自己）、
或任一方向的 `follow` 不存在。

> 本模块的 pytest 覆盖中，**"互关状态变化"的完整迁移链路没有独立用例**
> （详见本文件第 2 节的说明与 `docs/testing/API_DOCUMENT_AUDIT.md`）。
