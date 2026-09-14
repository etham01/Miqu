# 评论 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`CommentController` / `CommentServiceImpl` / `CommentCreateRequest` / `CommentVO`。

本模块 3 个接口。"发表/查询"挂在动态路径下（资源从属关系清晰），"删除"按评论 ID 直接定位。

---

## 目录

| # | 接口 | 方法 | 路径 | 认证 |
|---|---|---|---|---|
| 1 | 发表评论 | POST | `/api/posts/{id}/comments` | 🔒 |
| 2 | 评论列表 | GET | `/api/posts/{id}/comments` | — |
| 3 | 删除评论 | DELETE | `/api/comments/{id}` | 🔒（作者或管理员） |

---

## 1. 发表评论

`POST /api/posts/{id}/comments`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | `1` | **所属动态**的 ID |
| `content` | Body | string | ✅ | `"构图很棒，第三张尤其好看。"` | 非空；≤500 字符；服务端 `trim()` |

### 请求示例

```http
POST /api/posts/1/comments HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{ "content": "构图很棒，第三张的湖面倒影尤其好看。" }
```

### 响应

`data` 为 `CommentVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "112",
    "postId": "1",
    "content": "构图很棒，第三张的湖面倒影尤其好看。",
    "mine": true,
    "author": {
      "id": "5",
      "username": "test004",
      "nickname": "赵六",
      "avatar": "https://i.pravatar.cc/150?img=14",
      "bio": "咖啡爱好者 / 独立开发者。"
    },
    "createTime": "2026-09-12 14:30:00"
  }
}
```

`CommentVO` 字段：`id`(字符串)、`postId`(字符串)、`content`、`mine`、`author`(`UserBriefVO`)、`createTime`。

> 注意：`CommentVO` **没有独立的 `userId` 字段**，作者身份只能从 `author.id` 读取，
> 或直接用 `mine` 判断是否本人所发。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `评论内容不能为空` | `content` 缺失、空串或纯空格（`@NotBlank`） |
| 400 | `PARAM_INVALID` | `评论内容不能超过 500 个字符` | `content` 超长 |
| 400 | `PARAM_INVALID` | `请求体格式不正确` | 非法 JSON |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 423 | `USER_DISABLED` | `账号已被禁用，请联系管理员` | 当前用户被禁用 |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

> `ErrorCode.EMPTY_COMMENT(400, "评论内容不能为空")` **在代码中从未被抛出**：
> 空内容会被 DTO 上的 `@NotBlank` 提前拦下，返回的是 `PARAM_INVALID` 而非 `EMPTY_COMMENT`。
> 两者文案相同但**枚举名不同**（响应里只有 `code` 与 `message`，所以外部不可区分）。
> 该枚举目前是死代码，登记在审计报告中。

### 副作用

插入 `comment` → `post.comment_count +1` → 给动态作者生成**评论通知**（`type=3`，
带 `commentId` 与 ≤50 字符的内容快照）。
**评论自己的动态不产生通知**。

### 测试关注点

- 正向：发表成功 → 返回 `mine=true`；`post.commentCount` 精确 +1。
- 边界：500 字符 → 成功；501 → 400；空/纯空格 → 400。
- 动态不存在（如 id=`99999999`）→ 404。
- 未登录 → 401。
- 通知：他人评论产生 `type=3` 通知且含内容快照；**自评不通知**。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_comment.py::test_create_comment_increments_count` | 发表 + 计数 +1 |
| `tests/api/test_comment.py::test_comment_blank_content` | 空内容 400 |
| `tests/api/test_comment.py::test_comment_content_boundaries` | 长度边界（500/501） |
| `tests/api/test_comment.py::test_comment_on_missing_post` | 404 |
| `tests/api/test_comment.py::test_comment_requires_login` | 401 |
| `tests/api/test_notification.py::test_comment_creates_notification_with_snapshot` | 通知 + 快照 |
| `tests/api/test_notification.py::test_self_actions_do_not_notify` | 自评不通知 |

JUnit：`CommentControllerTest`（20 用例）。

---

## 2. 评论列表

`GET /api/posts/{id}/comments`

| 项 | 值 |
|---|---|
| 认证 | 不需要（白名单） |
| 成功业务码 | `200` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | — | 所属动态 ID |
| `page` | Query | integer | ❌ | `1` | ≥1 |
| `size` | Query | integer | ❌ | `10` | 1~50 |

**排序**：`createTime ASC, id ASC`（**时间正序**，先发的在前，读起来像对话）。

### 请求示例

```http
GET /api/posts/1/comments?page=1&size=10 HTTP/1.1
Host: localhost:8081
```

### 响应

`data` 为 `PageResult<CommentVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "1",
        "postId": "1",
        "content": "构图很棒，第三张的湖面倒影尤其好看。",
        "mine": false,
        "author": { "id": "3", "username": "test002", "nickname": "李四", "avatar": "...", "bio": "..." },
        "createTime": "2026-08-13 10:00:00"
      }
    ],
    "total": 5,
    "page": 1,
    "size": 10,
    "hasNext": false
  }
}
```

> **游客访问时 `mine` 恒为 `false`**（不是报错）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 测试关注点

- 正向：种子 `post` id=1 有 5 条评论 → `total == 5`。
- **排序**：严格时间正序（先发的在前）。
- 游客：可访问且所有 `mine=false`。
- 动态不存在 → 404。
- 动态被删除后 → 404。
- 分页与 `hasNext`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_comment.py::test_comment_list_is_ordered_ascending` | 正序 |
| `tests/api/test_comment.py::test_seed_post_has_five_comments` | 种子 5 条 |
| `tests/api/test_comment.py::test_guest_sees_mine_false` | 游客 `mine=false` |

JUnit：`CommentControllerTest`。

---

## 3. 删除评论

`DELETE /api/comments/{id}`

| 项 | 值 |
|---|---|
| 认证 | 需要（**作者本人或管理员**） |
| Content-Type | 无请求体 |
| 成功业务码 | `200` |

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `112` | **评论** ID（注意：不是动态 ID） |

### 请求示例

```http
DELETE /api/comments/112 HTTP/1.1
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
| 403 | `FORBIDDEN` | `无权限执行该操作` | **既不是评论作者也不是管理员** |
| 404 | `COMMENT_NOT_FOUND` | `评论不存在或已被删除` | 评论不存在或已删除（**重复删除也走这条**） |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 400 | `PARAM_INVALID` | `参数格式不正确：id` | `id` 非数字 |

### 副作用

逻辑删除 `comment` → **仅当所属动态仍然存在时**才 `post.comment_count -1`。
（动态已被删除时不再递减：那条动态的评论数已无意义，且此时查 `post` 会因逻辑删除返回 null。）

> 与作者自删**走同一套 Service**，管理员删除额外写一条 `DELETE_COMMENT` 操作日志。

### 测试关注点

- 作者自删 → 200；删除后 `post.commentCount` 精确 -1；列表 `total` -1。
- **越权**：删除他人评论 → 403 + 精确文案。
- 管理员可删任意评论 → 200。
- **重复删除** → 第二次 404（记住 `COMMENT_NOT_FOUND` 的文案是"评论不存在或已被删除"）。
- 未登录 → 401。
- **联动（推荐补充）**：删除动态后，其评论的删除接口也应返回 404（逻辑删除已过滤）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_comment.py::test_delete_own_comment_decrements_count` | 作者删除 + 计数 |
| `tests/api/test_comment.py::test_delete_others_comment_is_forbidden` | 403 |
| `tests/api/test_comment.py::test_admin_can_delete_any_comment` | 管理员删除 |
| `tests/api/test_comment.py::test_delete_comment_twice` | 重复删除 404 |
| `tests/api/test_comment.py::test_delete_comment_requires_login` | 401 |
| `tests/api/test_admin.py::test_admin_delete_comment` | 后台删除入口 |

JUnit：`CommentControllerTest`、`AdminContentControllerTest`。
