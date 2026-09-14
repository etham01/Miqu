# 私信 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`MessageController` / `MessageServiceImpl` / `MessageSendRequest` /
> `FollowStatusLoader.isMutual` / `MessageVO` / `MessageUnreadVO`。
> 会话相关接口（列表 / 打开 / 聊天记录 / 标记已读）见 [conversations.md](./conversations.md)。

本模块 2 个接口。发送消息**只需 `receiverId`**，会话由服务端自动创建或复用，
客户端不需要先调 `POST /api/conversations`。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 | 是否需要互关 |
|---|---|---|---|---|---|
| 1 | 发送私信 | POST | `/api/messages` | 🔒 | ✅ **需要** |
| 2 | 私信未读总数 | GET | `/api/messages/unread-count` | 🔒 | ❌ 不需要 |

---

## ⚠️ 互关规则：唯一业务入口

私信的前置条件**只有一处实现**，在 `FollowStatusLoader.isMutual(a, b)`：

```text
互关(A, B) ≡ isFollowing(A, B) && isFollowing(B, A)
```

`MessageServiceImpl.send` 与 `ConversationServiceImpl.openConversation` 都复用它。
只查两条 `follow` 记录是否存在，**不额外判断用户是否活跃**（活跃性由各自的 `requireActiveUser` 负责），
**不落状态字段、不加缓存**，**管理员不豁免**。

### 完整规则表（2026-09-11 冻结）

| 场景 | 发送消息 `POST /api/messages` | 打开会话 `POST /api/conversations` |
|---|---|---|
| 双方互关 | ✅ 200 | ✅ 200 |
| 都未关注 | ❌ **403 `NOT_MUTUAL_FOLLOW`** | ❌ **403 `NOT_MUTUAL_FOLLOW`** |
| 仅 A 关注 B | ❌ 403 | ❌ 403 |
| 仅 B 关注 A | ❌ 403 | ❌ 403 |
| 曾互关、现解除 | ❌ 403（不能发新消息） | ❌ 403（不能重新打开） |
| 解除后重新互关 | ✅ 200（自动恢复） | ✅ 200（自动恢复） |

| 只读/已读操作 | 是否受互关限制 |
|---|---|
| `GET /api/conversations`（会话列表） | ❌ 不受限 |
| `GET /api/conversations/{id}/messages`（历史消息） | ❌ 不受限（解除互关后**仍可读**） |
| `PUT /api/conversations/{id}/read`（标记已读） | ❌ 不受限（解除互关后**仍可标记**） |
| `GET /api/messages/unread-count` | ❌ 不受限 |

> 错误码固定为 `403` / `NOT_MUTUAL_FOLLOW` / message `需要互相关注后才能私聊`。
> **非互关时不得产生任何 `message` 或 `conversation` 数据**——互关校验发生在写入之前。

---

## 1. 发送私信

`POST /api/messages`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |
| 互关要求 | ✅ **需要** |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `receiverId` | long | ✅ | `3` | 接收者用户 ID；缺失 → 400 `接收者不能为空` |
| `content` | string | ✅ | `"在吗？想请教你一个问题。"` | 非空；≤1000 字符；服务端 `trim()`；`trim` 后为空 → 400 `消息内容不能为空` |

> **不需要 `conversationId`**：会话由服务端按 `(较小 ID, 较大 ID)` 规整后自动创建或复用。
> 客户端少一次往返，也避免了"会话还没建出来"的时序问题。

### 请求示例

```http
POST /api/messages HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "receiverId": 3, "content": "在吗？想请教你一个问题。" }
```

### 响应

`data` 为 `MessageVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "121",
    "conversationId": "1",
    "senderId": "2",
    "receiverId": "3",
    "content": "在吗？想请教你一个问题。",
    "mine": true,
    "isRead": false,
    "createTime": "2026-09-12 14:30:00"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `mine` | boolean | 对发送方恒为 `true`（这里是"我发出去的"） |
| `isRead` | boolean | 新消息恒为 `false` |

**失败（非互关）**：

```json
{ "code": 403, "message": "需要互相关注后才能私聊", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| **403** | **`NOT_MUTUAL_FOLLOW`** | **`需要互相关注后才能私聊`** | **双方不是互相关注**（含单向、解除互关后、存量非互关会话） |
| 400 | `CANNOT_MESSAGE_SELF` | `不能给自己发送私信` | `receiverId` == 当前用户 |
| 400 | `PARAM_INVALID` | `接收者不能为空` | 缺少 `receiverId` |
| 400 | `PARAM_INVALID` | `消息内容不能为空` | `content` 缺失、空串或纯空格 |
| 400 | `PARAM_INVALID` | `消息内容不能超过 1000 个字符` | `content` 超长 |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 非法 JSON |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 接收者不存在或已注销 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 接收者**或自己**被禁用 |

### ⚠️ 错误优先级（测试断言前必读）

校验顺序固定为：

```text
发送者活跃(404/423)
   ↓
不能给自己发(400)
   ↓
接收者活跃(404/423)
   ↓
互关校验(403 NOT_MUTUAL_FOLLOW)      ← 在内容校验与写入之前
   ↓
内容非空(400)
   ↓
创建/复用会话 → 写入 message
```

因此：

| 组合 | 实际返回 |
|---|---|
| 非互关 + 接收者不存在 | **404** `USER_NOT_FOUND`（不是 403） |
| 非互关 + 接收者被禁用 | **423** `USER_DISABLED`（不是 403） |
| 非互关 + 接收者是自己 | **400** `CANNOT_MESSAGE_SELF`（不是 403） |
| **非互关 + 内容为空** | **403** `NOT_MUTUAL_FOLLOW`（互关校验在内容校验**之前**） |
| | |
| 互关 + 内容为空 | 400 `消息内容不能为空` |

> 最后一行容易被忽略：**内容校验在互关校验之后**，
> 所以"非互关 + 空内容"返回的是 403 而不是 400。

### 副作用

同一事务内：

1. 复用或创建 `conversation`（按 `(min,max)` 规整，`uk_users` 兜底并发）；
2. 插入 `message`（`is_read=0`）；
3. **一条 SQL 原子更新**会话的 `last_message_id` / `last_message_preview`（截断 100 字符）/
   `last_message_time`，并把**接收方**的未读数 +1。

> 这两个动作在同一条 SQL 里完成，不会与并发消息互相覆盖。
> **发送方自己的未读数不增加。**
> **私信不写通知表**：私信提示由会话未读数承载，避免通知页与消息页重复。

### 测试关注点

#### 正常流程

- **互关用户发送消息成功** → 200，`data.content` 与提交一致、`mine=true`、`isRead=false`。
- **自动建会话** → 发送后 `GET /api/conversations` 的 `total == 1`，且 `partner.id` 是接收者。
- **已有会话继续发送** → 复用同一 `conversationId`，不新建。
- 接收方未读数 +1、**发送方未读数不变**。
- 会话 `lastMessagePreview` 更新，超 100 字符被截断。

#### 互关门槛（核心）

- **非互关发送 → 403 `需要互相关注后才能私聊`**（两个新注册、互不关注的用户）。
- **单向关注发送 → 403**：A 关注 B、B 未关注 A；以及反向 B→A 单向。
- **解除互关后发送 → 403**：A、B 曾互关并成功发过消息 → A 取消关注 → A 再发 → 403。
- **重新互关后恢复发送 → 200**。
- **存量非互关历史会话不能发送 → 403**：
  种子 `conversation` id=4（user 4↔6）、id=5（user 5↔7），双方并非互关。
- 非互关被拒时**不得落库**：断言该会话的 `message` 条数、接收方未读数**均未变化**。

#### 完整状态迁移链（不要拆成孤立用例）

```text
① 互关（A↔B）
      ↓
② 发送消息成功（200）
      ↓
③ 解除互关（A 取消关注 B）
      ↓
④ 历史消息仍可读取（GET /api/conversations/{id}/messages → 200）
      ↓
⑤ 仍可标记已读（PUT /api/conversations/{id}/read → 200）
      ↓
⑥ 发送新消息失败（403 NOT_MUTUAL_FOLLOW）
      ↓
⑦ 重新互关（A 再次关注 B）
      ↓
⑧ 再次发送成功（200）
```

每一步之间要验证数据状态（消息条数、未读数、会话是否新建），而不只是断言 HTTP 结果。
同时**用例之间必须相互隔离**（每次用新注册的随机用户，不依赖其他用例留下的数据）。

#### 异常与边界

- 给自己发 → 400 `不能给自己发送私信`。
- 接收者不存在（`99999999`）→ 404；接收者已注销（id=13）→ 404。
- 接收者被禁用（id=12）→ 423。
- 空内容 / 纯空格 → 400 `消息内容不能为空`。
- 内容 1000 字符 → 成功；1001 → 400。
- 未登录 → 401。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_send_message_creates_conversation` | 成功 + 自动建会话 + `partner` |
| `tests/api/test_message.py::test_unread_count_increments_for_receiver_only` | 只增接收方未读 |
| `tests/api/test_message.py::test_cannot_message_self` | 400 |
| `tests/api/test_message.py::test_message_content_boundaries` | 1000/1001 边界 |
| `tests/api/test_message.py::test_message_blank_content` | 空内容 400 |
| `tests/api/test_message.py::test_message_to_unknown_user` | 404 |
| `tests/api/test_message.py::test_send_message_requires_login` | 401 |
| `tests/api/test_message.py::test_conversation_list_orders_by_last_message` | 预览与排序 |

> ⚠️ **互关负向场景目前无 pytest 用例**：非互关发送 403、单向关注 403、解除互关后 403、
> 重新互关恢复 200、存量非互关会话不可发送——这些原先由 `test_message_mutual_follow.py`
> （10 用例）覆盖，**该文件当前不在仓库中**。详见
> [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)。
>
> 生产代码侧的规则已实现：`MessageServiceImpl.send` 中的
> `if (!followStatusLoader.isMutual(userId, receiverId)) throw BizException.of(ErrorCode.NOT_MUTUAL_FOLLOW);`
>
> `tests/api/test_message.py` 里的 `_make_mutual(a, b)` helper 是为**既有正向用例补互关前置**而加的，
> 它本身不测试互关规则。

JUnit：`MessageControllerTest`（17 用例，含互关前置改造）。

---

## 2. 私信未读总数

`GET /api/messages/unread-count`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| 成功业务码 | `200` |
| 互关要求 | 无 |

### 请求参数

无。

### 请求示例

```http
GET /api/messages/unread-count HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### 响应

`data` 为 `MessageUnreadVO`：

```json
{ "code": 200, "message": "success", "data": { "total": 3 } }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | **number** | 未读私信总条数 = `SUM(conversation.user1_unread 或 user2_unread)`（我的那一侧） |

> **为什么 `total` 是数字而不是字符串**：`JacksonConfig` 把**包装类型 `Long`** 序列化成字符串（防前端精度丢失），
> 但计数不是 ID，应当是数字。因此 `MessageUnreadVO.total` 声明为**基本类型 `long`**。
> 若用 `Map<String, Long>` 返回，自动装箱后的 `Long` 会被转成 `"3"`，与 `NotificationUnreadVO` 也不一致。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 当前用户被禁用 |

### 测试关注点

- 正向：`total` 等于各会话 `unreadCount` 之和（**权威值来自会话表，不扫消息表**）。
- 发送方未读数不变、接收方 +1（配合发送接口一起断言差值）。
- 标记会话已读后 `total` 相应减少。
- **私信不计入通知未读数**：`/api/messages/unread-count` 与
  `/api/notifications/unread-count` 是两套独立计数，同一件事不会在两处重复出现。
- 类型断言：`total` 必须是**数字**（`isinstance(total, int)`），不是字符串。
- 未登录 → 401。
- **未读数为 0 时不报错**，返回 `{"total": 0}`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_message.py::test_unread_count_increments_for_receiver_only` | 差值 |
| `tests/api/test_message.py::test_seed_user_unread_total` | 种子未读总数 |
| `tests/api/test_message.py::test_mark_conversation_read` | 已读后归零 |

JUnit：`MessageControllerTest`、`ResponseSerializationTest`（守 `total` 是数字）。
