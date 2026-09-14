# 会话 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`ConversationController` / `ConversationServiceImpl` / `MessageServiceImpl.listMessages` /
> `CreateConversationRequest` / `MessageQuery` / `ConversationVO` / `MessageVO`。

本模块 4 个接口。一期采用 **REST + 轮询**，不引入 WebSocket。
**私信前置条件（互关）只作用于"发起"（打开会话、发送消息），不影响历史读取。**

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 | 是否需要互关 |
|---|---|---|---|---|---|
| 1 | 会话列表 | GET | `/api/conversations` | 🔒 | ❌ 不需要 |
| 2 | 获取或创建会话 | POST | `/api/conversations` | 🔒 | ✅ **需要** |
| 3 | 聊天记录 | GET | `/api/conversations/{id}/messages` | 🔒 | ❌ 不需要 |
| 4 | 标记会话已读 | PUT | `/api/conversations/{id}/read` | 🔒 | ❌ 不需要 |

---

## ⚠️ 互关私聊规则（2026-09-11 冻结，规则权威定义）

| 场景 | 行为 |
|---|---|
| 会话双方**互相关注** | 可打开会话、可发送消息 |
| **非**互关（都未关注 / 仅单向） | `POST /api/conversations` → `403 NOT_MUTUAL_FOLLOW`；`POST /api/messages` → `403 NOT_MUTUAL_FOLLOW` |
| 已有历史会话**解除互关后** | **仍可** `GET /api/conversations/{id}/messages`、**仍可** `PUT /api/conversations/{id}/read`；**不可**再发消息、**不可**重新打开会话 |
| **重新互关后** | 发送与打开能力**自动恢复** |
| 会话列表 `GET /api/conversations` | **不受**互关限制，历史会话照常出现 |
| **管理员** | **不享有豁免**，同样需要互关 |
| 存量非互关历史会话（种子 `conversation` id=4、id=5） | 可读、不可发 |
| 互关判定 | `isFollowing(A,B) && isFollowing(B,A)`，**实时查询**，不落状态字段、不加缓存 |

错误码：`403` / `NOT_MUTUAL_FOLLOW` / message `需要互相关注后才能私聊`。

---

## 1. 会话列表

`GET /api/conversations`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| 成功业务码 | `200` |
| 互关要求 | **无** |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | integer | ❌ | `1` | ≥1 |
| `size` | integer | ❌ | `10` | 1~50 |

**排序**：`lastMessageTime DESC, id DESC`（最近有消息的在前）。

**只返回已经有消息的会话**：通过 `POST /api/conversations` 建出来但一条消息都没发过的空会话
（`lastMessageTime IS NULL`）**不出现在列表里**，避免列表出现空白条目。

### 请求示例

```http
GET /api/conversations?page=1&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `PageResult<ConversationVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "1",
        "partner": {
          "id": "3",
          "username": "test002",
          "nickname": "李四",
          "avatar": "https://i.pravatar.cc/150?img=13",
          "bio": "后端开发工程师，业余时间写写开源。"
        },
        "lastMessagePreview": "好的，那就这么定了。",
        "lastMessageTime": "2026-09-12 12:00:00",
        "unreadCount": 2
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
| `id` | string | 会话 ID（字符串） |
| `partner` | object | **对方**用户简要信息（服务端按当前用户自动取对方，前端不需要知道自己是谁） |
| `lastMessagePreview` | string | 最后一条消息预览，**超 100 字符已截断** |
| `lastMessageTime` | string | 最后一条消息时间；列表按此倒序 |
| `unreadCount` | number | **当前用户**在该会话的未读数（权威值，取 `conversation.user*_unread`，不实时扫消息表） |

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 当前用户被禁用 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |

### 测试关注点

- 正向：有消息的会话出现在列表，按 `lastMessageTime` 倒序。
- **空会话不出现**：调用 `POST /api/conversations` 建会话但**不发消息** → 列表 `total` 仍为 0。
- `partner` 是**对方**（不是自己）：A→B 的会话在 A 看来 `partner.id == B`。
- `unreadCount` 只统计"我收到的未读"：发送方未读数不变，接收方 +1。
- **解除互关后会话仍出现在列表、`unreadCount` 仍可读**（互关规则不作用于列表）。
- 未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_conversation_list_orders_by_last_message` | 倒序 |
| `tests/api/test_message.py::test_empty_conversation_is_hidden` | 空会话不显示 |
| `tests/api/test_message.py::test_seed_user_unread_total` | 种子未读数 |
| `tests/api/test_message.py::test_send_message_creates_conversation` | 发消息后会话出现且 `partner` 正确 |

JUnit：`ConversationControllerTest`（22 用例）。

---

## 2. 获取或创建会话

`POST /api/conversations`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |
| 互关要求 | ✅ **需要**（非互关 → 403） |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `targetUserId` | long | ✅ | `3` | 对方用户 ID；缺失 → 400 `对方用户不能为空` |

### 请求示例

```http
POST /api/conversations HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "targetUserId": 3 }
```

### 响应

`data` 为 `ConversationVO`（结构同"会话列表"的元素）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "6",
    "partner": { "id": "3", "username": "test002", "nickname": "李四", "avatar": "...", "bio": "..." },
    "lastMessagePreview": "",
    "lastMessageTime": null,
    "unreadCount": 0
  }
}
```

> **幂等**：已存在则直接返回原会话，不会重复创建。
> 服务端把两个用户 ID 规整成 `(较小, 较大)` 后再查找，
> 因此 `(A,B)` 与 `(B,A)` **一定命中同一条记录**。

> 新建的空会话 `lastMessageTime` 为 `null`，因此**暂时不会出现在会话列表**里，直到发出第一条消息。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| **403** | **`NOT_MUTUAL_FOLLOW`** | **`需要互相关注后才能私聊`** | **双方不是互相关注**（含单向、解除互关后） |
| 400 | `CANNOT_MESSAGE_SELF` | `不能给自己发送私信` | `targetUserId` == 当前用户 |
| 400 | `PARAM_INVALID` | `对方用户不能为空` | 缺少 `targetUserId` |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 目标用户不存在或已注销 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 目标用户**或自己**被禁用 |

**⚠️ 错误优先级（重要，测试断言前必读）**。校验顺序固定为：

```text
调用者活跃(404/423)
   ↓
不是自己(400)                       ← 注意：在互关判断之前
   ↓
目标活跃(404/423)
   ↓
互关(403 NOT_MUTUAL_FOLLOW)
```

因此：

| 组合 | 实际返回 |
|---|---|
| 非互关 + 目标不存在 | **404** `USER_NOT_FOUND`（不是 403） |
| 非互关 + 目标被禁用 | **423** `USER_DISABLED`（不是 403） |
| 非互关 + 目标是自己 | **400** `CANNOT_MESSAGE_SELF`（不是 403） |
| 非互关 + 目标正常存在 | **403** `NOT_MUTUAL_FOLLOW` |

### 测试关注点

- 正向：互关用户可打开会话；**幂等**（连调两次得到同一个 `id`）。
- **对称性（核心）**：A 调 `{targetUserId: B}` 与 B 调 `{targetUserId: A}` 必须返回**同一个会话 `id`**。
- **非互关**（新注册两个用户，彼此都没关注）→ **403 `需要互相关注后才能私聊`**。
- **单向关注**：A 关注 B、B 未关注 A → 403；反向（B→A 单向）同样 403。
- **解除互关后**：A、B 曾互关并已有会话 → A 取消关注 → A 再调本接口 → **403**（不能重新打开新的私聊）。
- **重新互关后**：恢复 200。
- 与自己发起 → 400 `不能给自己发送私信`。
- 目标不存在/已注销 → 404；目标被禁用 → 423。
- 未登录 → 401。
- **不产生副作用**：非互关被拒时，**不应创建任何 `conversation` 行**。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_conversation_is_symmetric` | A↔B 同一条会话 |
| `tests/api/test_message.py::test_open_conversation_is_idempotent` | 幂等 |
| `tests/api/test_message.py::test_empty_conversation_is_hidden` | 空会话不入列表 |

> ⚠️ **互关负向场景（非互关 403、解除互关后 403、重新互关恢复）目前没有 pytest 用例**：
> 原先覆盖它们的 `test_conversation_mutual_follow.py`（4 用例）当前不在仓库中
> （见 [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)）。
> 生产代码已实现该规则（`ConversationServiceImpl.openConversation` 中的 `isMutual` 校验）。

JUnit：`ConversationControllerTest`。

---

## 3. 聊天记录（游标分页）

`GET /api/conversations/{id}/messages`

| 项 | 值 |
|---|---|
| 认证 | 需要（**必须是会话参与者**） |
| 成功业务码 | `200` |
| 互关要求 | **无**（解除互关后仍可读） |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | — | 会话 ID |
| `beforeId` | Query | long | ❌ | — | 游标：只返回 `id < beforeId` 的消息（更早的）。首次不传，之后传上一页**最早一条**的 id |
| `size` | Query | integer | ❌ | `20` | 1~50 |

**返回顺序**：**时间正序**（服务端按 id 倒序查后再反转），前端可直接从上往下渲染。

**为什么用游标而不是 offset**：聊天记录不断从头部新增，用 `page=2` 翻历史会被新消息挤得错位/重复。
`beforeId` 取"id 小于它的 N 条"，无论期间新增多少条，翻页结果都稳定。

### 请求示例

```http
GET /api/conversations/1/messages?size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

翻上一页（传当前页最早一条的 id）：

```http
GET /api/conversations/1/messages?beforeId=101&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 是**数组**（不是 `PageResult`，因为游标分页没有 `total`）：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "101",
      "conversationId": "1",
      "senderId": "2",
      "receiverId": "3",
      "content": "在吗？想请教你一个问题。",
      "mine": true,
      "isRead": true,
      "createTime": "2026-09-12 10:00:00"
    },
    {
      "id": "102",
      "conversationId": "1",
      "senderId": "3",
      "receiverId": "2",
      "content": "在的，你说。",
      "mine": false,
      "isRead": false,
      "createTime": "2026-09-12 10:05:00"
    }
  ]
}
```

`MessageVO` 字段：`id`(字符串)、`conversationId`(字符串)、`senderId`(字符串)、`receiverId`(字符串)、
`content`、`mine`（是否当前用户发出，前端据此决定气泡左右）、`isRead`、`createTime`。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `CONVERSATION_NOT_FOUND` | `会话不存在` | 会话 ID 不存在 |
| 403 | `NOT_CONVERSATION_MEMBER` | `你不是该会话的参与者` | 是合法会话，但当前用户不是两个参与者之一 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 400 | `PARAM_INVALID` | `beforeId 必须大于 0` | `beforeId=0` 或负数 |
| 400 | `PARAM_INVALID` | `每页条数不能超过 50` | `size=51` |

> `404` 与 `403` 的区分是**有意设计**的：会话不存在 vs 会话存在但无权，两者可分别断言。

### 测试关注点

- 正向：历史消息返回，**时间正序**。
- **游标分页不重不漏（核心）**：造若干消息 → 第一页取最早一条的 id 作为 `beforeId` 翻页 →
  两页合并后 id 集合无交集、无遗漏。
- 非参与者访问 → 403 `你不是该会话的参与者`。
- 会话不存在（如 id=`99999999`）→ 404。
- **解除互关后仍可读（核心回归场景）**：
  A、B 曾互关并发过消息 → A 取消关注 → A 仍能 `GET` 到历史消息（**200**，不因非互关被拒）。
- **存量非互关历史会话可读**：种子 `conversation` id=4（user 4↔6）、id=5（user 5↔7）里
  `4` 与 `6` 并非互关，但仍应能读取历史消息。
- `mine` 字段：发送方为 `true`，接收方为 `false`。
- **`isRead` 与 `unreadCount` 是两套数据**：`isRead` 渲染单条已读态；
  未读总数取会话表的 `user*_unread`（后者才是权威值）。
- `size` 上限 50：传 51 应 400（不是静默截断）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_message_history_is_chronological` | 时间正序 |
| `tests/api/test_message.py::test_message_cursor_pagination` | 游标翻页不重不漏 |
| `tests/api/test_message.py::test_non_member_cannot_read_messages` | 非成员 403 |

> ⚠️ **"解除互关后历史仍可读"**、**"存量非互关会话可读"** 目前**没有 pytest 用例**
> （原 `test_message_mutual_follow.py` 覆盖，该文件当前不在仓库中）。详见审计报告。

JUnit：`ConversationControllerTest`、`MessageControllerTest`。

---

## 4. 标记该会话已读

`PUT /api/conversations/{id}/read`

| 项 | 值 |
|---|---|
| 认证 | 需要（**必须是会话参与者**） |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |
| 互关要求 | **无**（解除互关后仍可标记已读） |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `1` | 会话 ID |

### 请求示例

```http
PUT /api/conversations/1/read HTTP/1.1
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
| 404 | `CONVERSATION_NOT_FOUND` | `会话不存在` | 会话不存在 |
| 403 | `NOT_CONVERSATION_MEMBER` | `你不是该会话的参与者` | 不是该会话成员 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 副作用（同事务原子完成）

1. 把我收到的未读消息 `message.is_read=1`、写 `read_time`；
2. 把会话表上**我的** `user*_unread` 清零。

> 两者必须同事务：否则会出现"角标显示有未读，点进去却是空的"。
> 注意：**只影响自己**，不会把对方的未读一起清掉。
> 本接口是**幂等**的（已读状态再标记一次仍然成功）。

### 测试关注点

- 正向：标记后我的 `unreadCount` 归零、`GET /api/messages/unread-count` 相应减少。
- **隔离**：标记已读**不影响对方**的未读数（对方仍显示未读）。
- 非成员 → 403；会话不存在 → 404。
- **解除互关后仍可标记已读（核心回归场景）**：取消关注后 `PUT /read` 仍应 **200**。
- 幂等：连续标记两次都成功。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_mark_conversation_read` | 自己清零 |
| `tests/api/test_message.py::test_mark_read_does_not_affect_partner` | 不影响对方 |
| `tests/api/test_message.py::test_non_member_cannot_read_messages` | 非成员 403（同文件覆盖读写权限） |

> ⚠️ **"解除互关后仍可标记已读"** 目前**没有 pytest 用例**（原 `test_message_mutual_follow.py` 覆盖）。
