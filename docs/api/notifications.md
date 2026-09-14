# 通知 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`NotificationController` / `NotificationServiceImpl` / `NotificationQuery` /
> `NotificationVO` / `NotificationUnreadVO`。

本模块 4 个接口，全部需要登录。

**只有三类通知**：关注(1) / 点赞(2) / 评论(3)。
**私信不产生通知**——它由 `/api/messages/unread-count` 与会话列表的未读数承载，
避免同一件事在通知页与消息页重复出现。（`notification.type=4` 是保留值，一期不产生。）

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 通知列表 | GET | `/api/notifications` | 🔒 |
| 2 | 通知未读数 | GET | `/api/notifications/unread-count` | 🔒 |
| 3 | 单条标记已读 | PUT | `/api/notifications/{id}/read` | 🔒 |
| 4 | 全部标记已读 | PUT | `/api/notifications/read-all` | 🔒 |

---

## 1. 通知列表

`GET /api/notifications`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| 成功业务码 | `200` |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `type` | integer | ❌ | 不过滤 | `2` | `1` 关注 / `2` 点赞 / `3` 评论；越界 → 400 |
| `isRead` | integer | ❌ | 不过滤 | `0` | `0` 未读 / `1` 已读；越界 → 400 |
| `page` | integer | ❌ | `1` | `1` | ≥1 |
| `size` | integer | ❌ | `10` | `20` | 1~50 |

**排序**：`createTime DESC, id DESC`。
**隔离**：只返回**当前登录用户**收到的通知。

### 请求示例

```http
GET /api/notifications?type=2&isRead=0&page=1&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<NotificationVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "9",
        "type": 3,
        "actor": {
          "id": "5",
          "username": "test004",
          "nickname": "赵六",
          "avatar": "https://i.pravatar.cc/150?img=14",
          "bio": "咖啡爱好者 / 独立开发者。"
        },
        "postId": "1",
        "commentId": "2",
        "content": "请问用的什么镜头？",
        "isRead": false,
        "createTime": "2026-09-12 10:00:00"
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
| `id` | string | 通知 ID（字符串） |
| `type` | number | `1` 关注 / `2` 点赞 / `3` 评论 |
| `actor` | object | **触发者**信息（`UserBriefVO`，不含 email） |
| `postId` | string \| null | 相关动态 ID。**可能为 `null`，也可能指向已被删除的动态** |
| `commentId` | string \| null | 相关评论 ID |
| `content` | string | **内容快照**（评论摘要，≤50 字符）；关注/点赞类型为空串 `""` |
| `isRead` | boolean | 是否已读 |
| `createTime` | string | 通知时间 |

> **内容快照的意义**：关联的动态或评论被删除后，靠 `content` 仍能渲染出可读条目，不会变成空白。
> 前端点击跳转前应先请求详情确认，或直接容忍 404。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `通知类型只能是 1、2 或 3` | `type` 越界（如 4、0） |
| 400 | `PARAM_INVALID` | `已读状态只能是 0 或 1` | `isRead` 越界（如 2） |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |

### 测试关注点

- 正向：列表按时间倒序。
- **数据隔离（核心）**：A 的通知**不能**出现在 B 的列表里。
- **过滤**：`type=1/2/3` 各自只返回该类型；`isRead=0/1` 只返回未读/已读。
- **非法 `type`**（如 4 或 0）→ 400；非法 `isRead`（如 2）→ 400。
- `actor` 中**不得包含 `email`**。
- 关注/点赞类通知的 `content` 为空串；评论类带 ≤50 字快照。
- 关联动态被删除后，通知**仍然可见**（靠快照），但 `postId` 指向的对象可能已不存在。
- 未登录 → 401。

### 对应自动化测试

`tests/api/test_notification.py`（16 用例）：
`test_notifications_require_login`、`test_follow_creates_notification`、`test_unfollow_removes_notification`、
`test_like_creates_notification`、`test_unlike_removes_notification`、
`test_comment_creates_notification_with_snapshot`、`test_self_actions_do_not_notify`、
`test_mark_single_notification_read`、`test_mark_read_is_idempotent`、
`test_cannot_mark_others_notification`、`test_mark_all_read`、
`test_mark_all_read_does_not_affect_others`、`test_unread_count_shape`、
`test_notification_type_filter`、`test_notification_actor_has_no_email`、`test_invalid_type_filter`

JUnit：`NotificationControllerTest`（19 用例）。

---

## 2. 通知未读数（按类型拆开）

`GET /api/notifications/unread-count`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| 成功业务码 | `200` |

### 请求参数

无。

### 请求示例

```http
GET /api/notifications/unread-count HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `NotificationUnreadVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": { "total": 7, "follow": 1, "like": 4, "comment": 2 }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | **number** | 未读总数 = `follow + like + comment` |
| `follow` | number | 未读关注数 |
| `like` | number | 未读点赞数 |
| `comment` | number | 未读评论数 |

> 全部为**基本类型 `long`**，序列化后是数字（不是字符串）。
> **私信不计入**这四个字段。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |

### 测试关注点

- **结构断言**：`total == follow + like + comment`；四个字段都存在且为数字。
- 只有未读的才计入：把某条标记已读后对应类型计数 -1。
- **隔离**：只统计自己的通知。
- **私信不混入**：给对方发私信后，对方的通知未读**不应变化**。
- 无通知时返回全 0（不报错）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_notification.py::test_unread_count_shape` | 结构与合计一致 |

JUnit：`NotificationControllerTest`、`ResponseSerializationTest`（守计数是数字）。

---

## 3. 单条标记已读

`PUT /api/notifications/{id}/read`

| 项 | 值 |
|---|---|
| 认证 | 需要（**通知必须属于自己**） |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `9` | 通知 ID |

### 请求示例

```http
PUT /api/notifications/9/read HTTP/1.1
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
| 404 | `NOTIFICATION_NOT_FOUND` | `通知不存在` | 通知 ID 不存在 |
| 403 | `FORBIDDEN` | `无权限执行该操作` | **通知存在但不属于当前用户** |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

> **为什么"别人的通知"返回 403 而不是 404**：通知本身存在，只是无权操作。
> 两者区分开，测试才能分别断言（这是有意设计）。

### 测试关注点

- 正向：标记后该通知 `isRead=true`，对应类型未读数 -1。
- **幂等**：已读的再标记一次仍返回 **200**（不产生多余 UPDATE）。
- **越权**：操作他人通知 → 403 + 精确文案。
- 通知不存在 → 404。
- 未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_notification.py::test_mark_single_notification_read` | 正向 |
| `tests/api/test_notification.py::test_mark_read_is_idempotent` | 幂等 |
| `tests/api/test_notification.py::test_cannot_mark_others_notification` | 403 |

JUnit：`NotificationControllerTest`。

---

## 4. 全部标记已读

`PUT /api/notifications/read-all`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数

无。

### 请求示例

```http
PUT /api/notifications/read-all HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |

### 测试关注点

- 正向：调用后自己的未读数全部归零。
- **隔离**：**不影响他人**的未读数。
- **幂等空操作**：没有未读时执行**不报错**、不产生多余 UPDATE（前端可以放心在页面加载时无条件调用）。
- **只更新未读行**（`WHERE user_id = ? AND is_read = 0`），不重写已读记录的时间。
- 未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_notification.py::test_mark_all_read` | 全部已读 |
| `tests/api/test_notification.py::test_mark_all_read_does_not_affect_others` | 不影响他人 |

JUnit：`NotificationControllerTest`。

---

## 附：通知的生成与撤回

通知不是手工创建的，而是由其他业务动作**隐式产生 / 撤回**。测试时需要由此切入：

| 触发动作 | 产生通知 | 撤回时机 |
|---|---|---|
| `POST /api/users/{id}/follow` | `type=1` 关注通知给被关注者 | `DELETE /api/users/{id}/follow` 时撤回该条 |
| `POST /api/posts/{id}/like` | `type=2` 点赞通知给动态作者 | `DELETE /api/posts/{id}/like` 时撤回 |
| `POST /api/posts/{id}/comments` | `type=3` 评论通知给动态作者（带 ≤50 字快照） | 评论删除时**不撤回**（当前实现未处理） |
| 删除动态 | — | 该动态的**点赞/评论通知全部删除**，避免点进去 404 |

**统一规则**：`actor == 接收者` 时**不产生通知**（自己关注/点赞/评论自己）。
这条规则集中在 `NotificationService.create()` 里判断，避免各调用方遗漏。

> 因此**没有**"手动创建通知"的接口，测试通知只能通过上述业务动作间接构造。
