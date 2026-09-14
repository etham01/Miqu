# 管理后台 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`controller/admin/*`（6 个 Controller）/ `AdminServiceImpl` / `AdminLogServiceImpl` /
> `ReportServiceImpl.handle` / `UserServiceImpl.updateStatus` 及各自的 Query / Request / VO。

本模块 11 个接口。**全部需要管理员**（类上标 `@RequireAdmin`）。

---

## 目录

| # | 接口 | 方法 | 路径 | 权限 |
|---|---|---|---|---|
| 1 | 数据统计 | GET | `/api/admin/stats` | 管理员 |
| 2 | 用户列表 | GET | `/api/admin/users` | 管理员 |
| 3 | 用户详情 | GET | `/api/admin/users/{id}` | 管理员 |
| 4 | 禁用 / 解禁用户 | PUT | `/api/admin/users/{id}/status` | 管理员 |
| 5 | 动态列表 | GET | `/api/admin/posts` | 管理员 |
| 6 | 删除动态 | DELETE | `/api/admin/posts/{id}` | 管理员 |
| 7 | 评论列表 | GET | `/api/admin/comments` | 管理员 |
| 8 | 删除评论 | DELETE | `/api/admin/comments/{id}` | 管理员 |
| 9 | 举报列表 | GET | `/api/admin/reports` | 管理员 |
| 10 | 处理举报 | PUT | `/api/admin/reports/{id}/handle` | 管理员 |
| 11 | 操作日志列表 | GET | `/api/admin/logs` | 管理员 |

---

## 0. 访问控制（所有接口共用）

`/api/admin/**` **不在**免登录白名单里，因此失败原因分两层、可区分：

| 场景 | code | ErrorCode | message |
|---|---|---|---|
| 未登录 / Token 无效 | `401` | `UNAUTHORIZED` | `未登录或登录状态已失效` |
| 已登录但不是管理员 | `403` | `FORBIDDEN` | `无权限执行该操作` |
| 普通用户被禁用 | `423` | `USER_DISABLED` | `账号已被禁用，请联系管理员` |

> 两种失败的**文案不同**，前端据此判断该引导登录还是提示无权限。
> 判断依据：`@RequireAdmin` 命中时，`currentUser == null` → 401；`!isAdmin()` → 403。

---

## 1. 数据统计

`GET /api/admin/stats`

### 请求参数

无。

### 请求示例

```http
GET /api/admin/stats HTTP/1.1
Host: localhost:8081
Authorization: Bearer <admin token>
```

### 响应

`data` 为 `AdminStatsVO`（**全部字段为基本类型 `long`，序列化后是数字**）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userTotal": 21,
    "postTotal": 40,
    "commentTotal": 111,
    "todayNewUser": 0,
    "todayNewPost": 0,
    "todayNewComment": 0,
    "pendingReportTotal": 3
  }
}
```

| 字段 | 说明 |
|---|---|
| `userTotal` / `postTotal` / `commentTotal` | 总数，**不含逻辑删除的行**（已注销用户、已删动态/评论都不计） |
| `todayNewUser` / `todayNewPost` / `todayNewComment` | 今日新增（当天 `00:00:00` 起） |
| `pendingReportTotal` | **待处理**举报数（`status=0`），用于后台首页待办提醒 |

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 未登录 |
| 403 | `FORBIDDEN` | `无权限执行该操作` | 非管理员 |

### 测试关注点

- **结构**：7 个字段齐全且都是**数字**。
- **口径**：`userTotal` **不含已注销用户**（种子 `deleted001` 不计入）。
- **差值断言**（避免依赖绝对值）：注册一个新用户 → `userTotal` **+1**；
  删除一条动态 → `postTotal` **-1**。
- `pendingReportTotal` 随举报处理而减少。
- 今日新增：当天无操作时为 0。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_stats_shape` | 字段结构 |
| `tests/api/test_admin.py::test_stats_user_total_increments_after_register` | 注册后 +1 |
| `tests/api/test_admin.py::test_stats_post_total_decreases_after_delete` | 删动态后 -1 |
| `test_admin_endpoints_require_login` / `test_admin_endpoints_forbid_normal_user` / `test_admin_endpoints_allow_admin` | 访问控制矩阵（参数化含本接口） |

JUnit：`AdminStatsControllerTest`（9 用例）。

---

## 2. 用户列表

`GET /api/admin/users`

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `keyword` | string | ❌ | 不过滤 | `"test"` | 匹配**用户名或昵称**；≤32 字符；`%`/`_`/`\` 会被转义 |
| `status` | integer | ❌ | 不过滤 | `1` | `1` 正常 / `0` 禁用；越界 400 |
| `page` | integer | ❌ | `1` | `1` | ≥1 |
| `size` | integer | ❌ | `10` | `20` | 1~50 |

**排序**：`createTime DESC, id DESC`。

### 请求示例

```http
GET /api/admin/users?keyword=test&status=1&page=1&size=20 HTTP/1.1
Host: localhost:8081
Authorization: Bearer <admin token>
```

### 响应

`data` 为 `PageResult<AdminUserVO>`：

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
        "email": "test001@miqu.com",
        "avatar": "https://i.pravatar.cc/150?img=12",
        "role": 1,
        "status": 1,
        "followingCount": 6,
        "followerCount": 14,
        "postCount": 1,
        "createTime": "2026-05-25 10:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20,
    "hasNext": false
  }
}
```

> 与 `UserVO` 的区别：**含 `status`**（管理员最关心的字段），同样**不含密码**。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `搜索关键词不能超过 32 个字符` | `keyword` 超长 |
| 400 | `PARAM_INVALID` | `状态只能是 0 或 1` | `status` 越界 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |
| 401 / 403 | 同上 | — | 未登录 / 非管理员 |

### 测试关注点

- 正向：列表返回全部活跃用户。
- **已注销用户不出现**（逻辑删除自动过滤）。
- **状态过滤**：`status=0` 只返回禁用用户、`status=1` 只返回正常用户。
- **通配符转义**：`keyword=%` 不应返回全部用户。
- `status=9` → 400。
- **不得包含 `password`**。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_user_list` | 列表 |
| `tests/api/test_admin.py::test_user_list_hides_deleted_user` | 隐藏已注销 |
| `tests/api/test_admin.py::test_user_list_filter_by_status` | 状态过滤 |
| `tests/api/test_admin.py::test_user_list_keyword_wildcard_is_escaped` | 通配符转义 |
| `tests/api/test_admin.py::test_user_list_invalid_status` | 400 |

JUnit：`AdminUserControllerTest`（14 用例）。

---

## 3. 用户详情

`GET /api/admin/users/{id}`

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `2` | 用户 ID |

### 响应

`data` 为单个 `AdminUserVO`（字段同"用户列表"的元素）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `USER_NOT_FOUND` | `用户不存在` | 用户不存在**或已注销** |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 测试观察点

- 正向：`id=2` 返回 `test001` 的信息，含 `status`。
- 不存在的 id → 404。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_user_detail_not_found` | 404 |
| `test_admin_endpoints_allow_admin`（参数含 `GET /api/admin/users/2`） | 正向可访问 |

JUnit：`AdminUserControllerTest`。

---

## 4. 禁用 / 解禁用户

`PUT /api/admin/users/{id}/status`

| 项 | 值 |
|---|---|
| 认证 | 管理员 |
| Content-Type | `application/json` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | `12` | 目标用户 ID |
| `status` | Body | integer | ✅ | `0` | `1` 正常 / `0` 禁用；缺失 → 400 `状态不能为空`；越界 → 400 |

### 请求示例

```http
PUT /api/admin/users/12/status HTTP/1.1
Host: localhost:8081
Authorization: Bearer <admin token>
Content-Type: application/json

{ "status": 0 }
```

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `CANNOT_OPERATE_SELF` | `不能对自己执行该操作` | `id` == 当前管理员 ID（**在最前面判断**） |
| 400 | `PARAM_INVALID` | `状态不能为空` | 缺少 `status` |
| 400 | `PARAM_INVALID` | `状态只能是 0（禁用）或 1（正常）` | `status` 越界（如 9） |
| 403 | `CANNOT_DISABLE_ADMIN` | `不能禁用管理员账号` | 目标是管理员账号（`role=2`） |
| 404 | `USER_NOT_FOUND` | `用户不存在` | 目标不存在或已注销 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

**⚠️ 校验顺序（决定实际返回哪个错误）**：

```text
1. id == 当前管理员  → 400 CANNOT_OPERATE_SELF   ← 最优先
2. 目标存在且未注销   → 404 USER_NOT_FOUND
3. 目标不是管理员     → 403 CANNOT_DISABLE_ADMIN
4. 更新 status
```

### 副作用

1. 更新 `user.status`；
2. 写一条**管理员操作日志**：`operationType = DISABLE_USER`（禁用）或 `ENABLE_USER`（解禁），
   `targetType=1`，`detail = "禁用用户 #12"` / `"解禁用户 #12"`。

> **禁用立即生效**：认证过滤器每请求查库校验状态，
> 被禁用者手里**已签发的 Token 会马上失效**（返回 423），而不是等 7 天过期。
> 注意：**解除禁用（`status=1`）不会恢复旧 Token 的"有效性"概念**——
> 实际上无状态 JWT 从未失效，只是被查库拦下；解禁后旧 Token 仍可用。

### 测试关注点

- 正向：禁用 → 200；该用户旧 Token 访问受保护接口立即 **423**，且**无法重新登录**（423）。
- 解禁：`status=1` → 200，用户恢复可用。
- **不能操作自己** → 400 `不能对自己执行该操作`（管理员把自己当目标）。
- **参数**：`status=9` → 400；缺 `status` → 400。
- 目标不存在/已注销 → 404。
- **日志落库**：操作后 `GET /api/admin/logs` 应能查到对应的 `DISABLE_USER` / `ENABLE_USER` 记录。
- ⚠️ **"不能禁用管理员账号"（403 `CANNOT_DISABLE_ADMIN`）目前无法通过 HTTP 触达**：
  种子只有一个 `admin`，而管理员无法把自己作为禁用目标（第 1 条先命中 400）。
  这是**已记录的真实覆盖缺口**，不是遗漏（要覆盖需在 `data.sql` 里加第二个管理员）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_disable_and_enable_user` | 禁用 + 解禁 |
| `tests/api/test_admin.py::test_cannot_disable_self` | 400 不能操作自己 |
| `tests/api/test_admin.py::test_disable_self_is_blocked_before_admin_guard` | 校验顺序（400 先于 403） |
| `tests/api/test_admin.py::test_invalid_status_value` | 400 |
| `tests/api/test_admin.py::test_disabled_seed_user_can_be_reenabled` | 解禁种子禁用账号 |
| `tests/api/test_admin.py::test_admin_action_writes_log` | 操作日志落库 |
| `tests/api/test_auth.py::test_me_returns_403_after_account_disabled` | 禁用后旧 Token 立即失效（423） |

JUnit：`AdminUserControllerTest`、`AdminAccessControlTest`。

---

## 5. 动态列表

`GET /api/admin/posts`

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `userId` | long | ❌ | 不过滤 | `2` | 按作者过滤 |
| `keyword` | string | ❌ | 不过滤 | `"测试"` | 匹配动态内容；≤32 字符；通配符转义 |
| `page` / `size` | integer | ❌ | `1` / `10` | — | 同上 |

**排序**：`createTime DESC, id DESC`。
**只列出未删除的动态**（逻辑删除自动过滤）——后台列表的用途是"管理现存内容"，
翻已删内容属审计范畴，交给操作日志。

### 响应

`data` 为 `PageResult<AdminPostVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "1",
        "content": "周末去了趟青海湖，天气好得不像话。整理了一组照片分享给大家 🌊",
        "images": ["https://picsum.photos/seed/miqu1_0/800/600"],
        "author": { "id": "2", "username": "test001", "nickname": "张三", "avatar": "...", "bio": "..." },
        "likeCount": 10,
        "commentCount": 5,
        "createTime": "2026-08-13 10:00:00"
      }
    ],
    "total": 40,
    "page": 1,
    "size": 10,
    "hasNext": true
  }
}
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `搜索关键词不能超过 32 个字符` | `keyword` 超长 |
| 400 | `PARAM_INVALID` | `页码必须大于 0` / `每页条数不能超过 50` | 分页越界 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 测试关注点

- 正向：列表返回动态、含图片与作者信息。
- 过滤：按 `userId`、按 `keyword`。
- 已删除的动态**不出现**。
- 通配符转义（`keyword=%` 不返回全部）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_post_list` | 列表 |

JUnit：`AdminContentControllerTest`（13 用例）。

---

## 6. 删除动态（后台）

`DELETE /api/admin/posts/{id}`

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `12` | 动态 ID |

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | 动态不存在或已删除（重复删除也走这条） |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 副作用

与**作者自删走同一套 Service**（`PostService.delete`）：逻辑删除动态与评论、
物理删除图片与点赞、回滚作者动态数、清理该动态的通知；
**额外**写一条 `DELETE_POST` 操作日志。

### 测试关注点

- 正向：删除 → 200；动态详情 404、列表不再出现。
- **级联清理**：评论也被删除、计数回滚、相关通知清理。
- 重复删除 → 404。
- **日志**：产生 `DELETE_POST` 记录。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_admin_delete_post` | 后台删除 |
| `tests/api/test_post.py::test_admin_can_delete_any_post` | 管理员可删他人动态 |

JUnit：`AdminContentControllerTest`。

---

## 7. 评论列表

`GET /api/admin/comments`

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `postId` | long | ❌ | 不过滤 | `1` | 按所属动态过滤 |
| `userId` | long | ❌ | 不过滤 | `3` | 按评论者过滤 |
| `keyword` | string | ❌ | 不过滤 | `"测试"` | 匹配评论内容；≤32 字符；通配符转义 |
| `page` / `size` | integer | ❌ | `1` / `10` | — | 同上 |

**排序**：`createTime DESC, id DESC`。

### 响应

`data` 为 `PageResult<AdminCommentVO>`：

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
        "author": { "id": "3", "username": "test002", "nickname": "李四", "avatar": "...", "bio": "..." },
        "createTime": "2026-08-13 10:00:00"
      }
    ],
    "total": 111,
    "page": 1,
    "size": 10,
    "hasNext": true
  }
}
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `搜索关键词不能超过 32 个字符` | `keyword` 超长 |
| 400 | `PARAM_INVALID` | 分页越界文案 | `page` / `size` 越界 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 测试关注点

- 正向：列表返回评论与评论者信息。
- 过滤：按 `postId`、按 `userId`、按 `keyword`。
- 已删除评论不出现。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_comment_list` | 列表 |

JUnit：`AdminContentControllerTest`。

---

## 8. 删除评论（后台）

`DELETE /api/admin/comments/{id}`

### 请求参数（Path）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `id` | long | ✅ | `112` | 评论 ID |

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | `评论不存在或已被删除` | 评论不存在或已删除 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 副作用

逻辑删除评论；**仅当所属动态未被删除时**才回滚其评论数；写一条 `DELETE_COMMENT` 操作日志。

### 测试关注点

- 正向：删除 → 200；评论列表不再出现、`post.commentCount` 回滚。
- 重复删除 → 404。
- 日志产生 `DELETE_COMMENT`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_admin_delete_comment` | 后台删除 |

JUnit：`AdminContentControllerTest`。

---

## 9. 举报列表

`GET /api/admin/reports`

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `status` | integer | ❌ | 不过滤 | `0` | `0` 待处理 / `1` 已处理 / `2` 已驳回；越界 400 |
| `page` / `size` | integer | ❌ | `1` / `10` | — | 同上 |

**排序**：`createTime DESC, id DESC`。

### 响应

`data` 为 `PageResult<AdminReportVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "1",
        "reporter": { "id": "5", "username": "test004", "nickname": "赵六", "avatar": "...", "bio": "..." },
        "targetType": 2,
        "targetId": "11",
        "targetPreview": "动态：这是一条测试动态 #11，用于验证首页列表、分页与计数逻辑。",
        "reasonType": 1,
        "reasonDetail": "疑似营销广告内容",
        "status": 0,
        "handler": null,
        "handleRemark": "",
        "handleTime": null,
        "createTime": "2026-09-07 10:00:00"
      }
    ],
    "total": 3,
    "page": 1,
    "size": 10,
    "hasNext": false
  }
}
```

| 字段 | 说明 |
|---|---|
| `reporter` | 举报人信息（`UserBriefVO`） |
| `targetType` / `targetId` | 被举报对象（多态外键） |
| `targetPreview` | **服务端解析出的目标摘要**：`用户：昵称（@username）` / `动态：内容前 40 字…` / `评论：内容前 40 字…`；**目标已被删除时为 `（内容已被删除）`** |
| `handler` | 处理人信息；**未处理时为 `null`** |
| `handleRemark` / `handleTime` | 处理备注与时间 |

> `targetPreview` 必须在服务端做：`targetId` 是多态外键，
> 前端单看 `(targetType, targetId)` 无法展示"被举报的是什么"，逐条查又会 N+1。
> 服务端按类型分组批量查询最划算。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `处理状态只能是 0、1 或 2` | `status` 越界 |
| 400 | `PARAM_INVALID` | 分页越界文案 | `page` / `size` 越界 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 测试关注点

- 正向：列表返回举报 + `reporter` + `targetPreview`。
- **`targetPreview` 正确性（核心）**：动态类举报显示动态摘要；已删除目标显示提示文案。
- 过滤：`status=0` 只返回待处理；`status=1/2` 同理。
- 未处理时 `handler` 为 `null`、`handleTime` 为 `null`。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_report_is_visible_in_admin_queue` | 待处理队列可见 |
| `tests/api/test_admin.py::test_report_list_has_target_preview` | `targetPreview` |

JUnit：`AdminReportControllerTest`（18 用例）。

---

## 10. 处理举报

`PUT /api/admin/reports/{id}/handle`

| 项 | 值 |
|---|---|
| 认证 | 管理员 |
| Content-Type | `application/json` |

### 请求参数

| 参数 | 位置 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|---|
| `id` | Path | long | ✅ | `1` | 举报 ID |
| `status` | Body | integer | ✅ | `1` | `1` 已处理（违规成立）/ `2` 已驳回（未违规） |
| `handleRemark` | Body | string | ❌ | `"已确认违规"` | ≤255 字符 |
| `action` | Body | string | ❌ | `"DELETE_POST"` | 处置动作：`NONE`（默认）/ `DELETE_POST` / `DELETE_COMMENT` / `DISABLE_USER`；**大小写不敏感** |

**处置动作与目标类型的匹配约束**：

| `action` | 允许的 `targetType` |
|---|---|
| `NONE` | 任意（不处置） |
| `DELETE_POST` | 仅 `targetType=2`（动态） |
| `DELETE_COMMENT` | 仅 `targetType=3`（评论） |
| `DISABLE_USER` | 仅 `targetType=1`（用户） |

### 请求示例

```http
PUT /api/admin/reports/1/handle HTTP/1.1
Host: localhost:8081
Authorization: Bearer <admin token>
Content-Type: application/json

{ "status": 1, "handleRemark": "已确认违规，删除该动态", "action": "DELETE_POST" }
```

### 响应

```json
{ "code": 200, "message": "success", "data": null }
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `处理结论不能为空` | 缺少 `status` |
| 400 | `PARAM_INVALID` | `处理结论只能是 1（已处理）或 2（已驳回）` | `status` 越界 |
| 400 | `PARAM_INVALID` | `处理备注不能超过 255 个字符` | `handleRemark` 超长 |
| 400 | `INVALID_REPORT_ACTION` | `不支持的处置动作` | `action` 不是四个枚举值之一（如 `"FOO"`） |
| 400 | `REJECT_WITH_ACTION_NOT_ALLOWED` | `驳回举报时不能同时执行处置动作` | `status=2` 且 `action != NONE` |
| 400 | `REPORT_ACTION_MISMATCH` | `处置动作与举报目标类型不匹配` | 如对动态类举报传 `DELETE_COMMENT` |
| 404 | `NOT_FOUND` | `举报不存在` | 举报 ID 不存在（**注意文案与枚举名，`ErrorCode.NOT_FOUND`**） |
| 409 | `REPORT_ALREADY_HANDLED` | `该举报已被处理` | 举报**不是**待处理状态（防处置动作被执行两次） |
| 401 / 403 | — | — | 未登录 / 非管理员 |

**⚠️ 校验顺序**：

```text
举报存在(404) → 仍是待处理(409)
   ↓
驳回 + 有处置动作(400 REJECT_WITH_ACTION_NOT_ALLOWED)
   ↓
动作与目标类型匹配(400 REPORT_ACTION_MISMATCH)
   ↓
执行处置动作 → 更新举报状态 → 写操作日志
```

### 处置动作的容错

执行处置时若目标**已被作者本人删除**（`POST_NOT_FOUND` / `COMMENT_NOT_FOUND` / `USER_NOT_FOUND`），
**举报仍可正常结案**（只记日志，不报错）——因为"内容已经没了"本身不阻塞结案。
其余异常照常抛出。

### 副作用

1. 按 `action` 执行处置（删动态 / 删评论 / 禁用用户）；
2. 更新举报：`status`、`handlerId`、`handleRemark`、`handleTime`；
3. 写一条 `HANDLE_REPORT` 操作日志，
   `targetType=4`（举报），`detail = "处理举报 #1 → 已处理，处置动作：DELETE_POST"`。

### 测试关注点

- 正向：`status=1` + `action=NONE` → 200，举报变已处理、`handler` 与 `handleTime` 非空。
- **处置动作生效**：`DELETE_POST` → 被举报动态随后 404；`DISABLE_USER` → 目标用户被禁用。
- **驳回**：`status=2` + 不传 `action` → 200（不处置内容）。
- **非法组合（重点）**：
  - `status=2` + `action=DELETE_POST` → 400 `驳回举报时不能同时执行处置动作`；
  - 动态类举报 + `action=DELETE_COMMENT` → 400 `处置动作与举报目标类型不匹配`；
  - `action="FOO"` → 400 `不支持的处置动作`。
- **重复处理**：对已处理的举报再处理 → 409 `该举报已被处理`（**不能执行两次处置**）。
- 举报不存在 → 404 `举报不存在`。
- `status=3` → 400；缺 `status` → 400。
- 处置动作**大小写不敏感**（`delete_post` 也可）。
- 日志：产生 `HANDLE_REPORT` 记录。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_handle_report_with_delete_action` | 已处理 + 删动态 |
| `tests/api/test_admin.py::test_handle_report_reject` | 驳回 |
| `tests/api/test_admin.py::test_reject_with_action_is_rejected` | 驳回带动作 → 400 |
| `tests/api/test_admin.py::test_action_must_match_target_type` | 动作/类型不匹配 → 400 |
| `tests/api/test_admin.py::test_invalid_report_action` | 非法动作 → 400 |
| `tests/api/test_admin.py::test_handle_report_twice_is_conflict` | 重复处理 409 |
| `tests/api/test_admin.py::test_handle_missing_report` | 举报不存在 404 |

JUnit：`AdminReportControllerTest`。

---

## 11. 操作日志列表

`GET /api/admin/logs`

| 项 | 值 |
|---|---|
| 认证 | 管理员 |
| 备注 | 日志是**只读**的：没有修改与删除接口 |

### 请求参数（Query）

| 参数 | 类型 | 必填 | 默认 | 示例 | 说明 |
|---|---|---|---|---|---|
| `adminId` | long | ❌ | 不过滤 | `1` | 按操作管理员过滤 |
| `operationType` | string | ❌ | 不过滤 | `"DELETE_POST"` | **精确匹配**（不是模糊）；空白值视为不过滤 |
| `page` / `size` | integer | ❌ | `1` / `10` | — | 同上 |

**排序**：`createTime DESC, id DESC`。

### 响应

`data` 为 `PageResult<AdminOperationLogVO>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "5",
        "admin": { "id": "1", "username": "admin", "nickname": "系统管理员", "avatar": "...", "bio": "..." },
        "operationType": "DELETE_POST",
        "targetType": 2,
        "targetId": "12",
        "detail": "删除动态 #12",
        "ip": "127.0.0.1",
        "createTime": "2026-09-09 10:00:00"
      }
    ],
    "total": 4,
    "page": 1,
    "size": 10,
    "hasNext": false
  }
}
```

| 字段 | 说明 |
|---|---|
| `admin` | 操作人信息 |
| `operationType` | `DISABLE_USER` / `ENABLE_USER` / `DELETE_POST` / `DELETE_COMMENT` / `HANDLE_REPORT` |
| `targetType` | `1` 用户 / `2` 动态 / `3` 评论 / **`4` 举报** |
| `detail` | 操作摘要，≤500 字符，**不含任何敏感信息** |
| `ip` | 客户端 IP（优先取 `X-Forwarded-For` 第一段，否则 `getRemoteAddr`）。**仅供参考，不作为鉴权依据**（该头可伪造） |

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | 分页越界文案 | `page` / `size` 越界 |
| 401 / 403 | — | — | 未登录 / 非管理员 |

### 测试关注点

- 正向：种子有 4 条日志（`DELETE_POST`、两条 `HANDLE_REPORT`、`DISABLE_USER`）。
- **写日志联动**：执行禁用用户 / 删除动态 / 删除评论 / 处理举报后，
  日志列表**新增**一条对应 `operationType` 的记录。
- 过滤：按 `adminId`、按 `operationType`（精确匹配）。
- **不含敏感信息（安全）**：`detail`、`ip` 等字段**不得出现密码、Token**。
- 日志**不可修改、不可删除**（没有对应接口，无法通过 HTTP 篡改）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_operation_logs` | 列表 |
| `tests/api/test_admin.py::test_admin_action_writes_log` | 操作后落库 |
| `tests/api/test_admin.py::test_operation_log_filter` | 过滤 |
| `tests/api/test_admin.py::test_operation_log_has_no_secrets` | 不含敏感信息 |

JUnit：`AdminContentControllerTest`、`AdminUserControllerTest`。

---

## 附：后台访问控制矩阵的实际测试范围

`tests/api/test_admin.py` 的 `ADMIN_ENDPOINTS` 常量包含 **7 个 GET 接口**：

```text
GET /api/admin/stats
GET /api/admin/users
GET /api/admin/users/2
GET /api/admin/posts
GET /api/admin/comments
GET /api/admin/reports
GET /api/admin/logs
```

对每个接口断言 未登录 401 / 普通用户 403 / 管理员 200（`7 × 3 = 21` 个用例）。

**后台的写接口**（`PUT users/{id}/status`、`DELETE posts/{id}`、`DELETE comments/{id}`、
`PUT reports/{id}/handle`）**不在该矩阵中**，
它们由各自的业务用例覆盖（含 403 越权断言），但**没有矩阵式的"未登录 401 / 非管理员 403"验证**。

> 说明：`tests/README.md` 把该矩阵描述为"11 个后台接口 × …"，写在 **pytest** 那一行，
> 但 pytest 实际只覆盖 **7 个**；"11"是 **JUnit** 侧 `AdminAccessControlTest` 的子集大小
> （7 个 + `posts/1` + `comments/1` + `users/2/status` + `reports/1/handle`）。
> 详见 [API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md) 的 B-04。
