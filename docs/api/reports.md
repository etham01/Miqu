# 举报 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`ReportController` / `ReportServiceImpl` / `CreateReportRequest` / `ReportVO`。
> 举报的**处理**在管理后台，见 [admin.md](./admin.md)。

本模块 1 个接口（提交举报）。举报进入**待处理队列**，由管理员在后台处理。

---

## 1. 提交举报

`POST /api/reports`

| 项 | 值 |
|---|---|
| 认证 | 需要 |
| Content-Type | `application/json` |
| 成功业务码 | `200` |

### 请求参数（JSON Body）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `targetType` | integer | ✅ | `2` | `1` 用户 / `2` 动态 / `3` 评论；**越界 400** |
| `targetId` | long | ✅ | `11` | 被举报对象的 ID（**多态外键**，含义由 `targetType` 决定） |
| `reasonType` | integer | ✅ | `1` | `1` 垃圾广告 / `2` 辱骂骚扰 / `3` 色情低俗 / `4` 违法违规 / `5` 其他 |
| `reasonDetail` | string | ❌ | `"疑似营销广告内容"` | ≤255 字符；不传存空串 |

### 请求示例

```http
POST /api/reports HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "targetType": 2,
  "targetId": 11,
  "reasonType": 1,
  "reasonDetail": "疑似营销广告内容"
}
```

### 响应

`data` 为 `ReportVO`（**提交回执，不含处理结果**）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "6",
    "targetType": 2,
    "targetId": "11",
    "createTime": "2026-09-12 14:30:00"
  }
}
```

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `PARAM_INVALID` | `举报目标类型不能为空` | 缺少 `targetType` |
| 400 | `PARAM_INVALID` | `举报目标类型只能是 1、2 或 3` | `targetType` 越界（**含传 4**） |
| 400 | `PARAM_INVALID` | `举报目标不能为空` | 缺少 `targetId` |
| 400 | `PARAM_INVALID` | `举报原因不能为空` | 缺少 `reasonType` |
| 400 | `PARAM_INVALID` | `举报原因取值不合法` | `reasonType` 不在 1~5 |
| 400 | `PARAM_INVALID` | `补充说明不能超过 255 个字符` | `reasonDetail` 超长 |
| 400 | `CANNOT_REPORT_SELF` | `不能举报自己` | 目标的所有者就是当前用户 |
| 400 | `INVALID_TARGET_TYPE` | `举报目标类型不合法` | Service 层兜底判断 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 404 | `USER_NOT_FOUND` | `用户不存在` | `targetType=1` 且目标用户不存在/已注销 |
| 404 | `POST_NOT_FOUND` | `动态不存在或已被删除` | `targetType=2` 且目标动态不存在/已删除 |
| 404 | `COMMENT_NOT_FOUND` | `评论不存在或已被删除` | `targetType=3` 且目标评论不存在/已删除 |
| 409 | `ALREADY_REPORTED` | `你已经举报过该内容` | 同一用户对同一 `(targetType, targetId)` 重复举报（并发下由 `uk_reporter_target` 兜底） |

> ⚠️ **`INVALID_TARGET_TYPE` 实际不可达**：DTO 上的 `@Min(1)`+`@Max(3)` 会先把 `targetType=4`
> 拦成 `400 参数校验失败`（`PARAM_INVALID`）并给出"举报目标类型只能是 1、2 或 3"。
> 因此响应里的"举报目标类型不合法"这一句在 HTTP 层拿不到。
> 该枚举分支属兜底代码，登记在审计报告中。

**校验顺序**：目标类型合法 → 解析目标所有者（不存在则 404）→ 不能举报自己（400）→ 是否重复举报（409）。

### 测试关注点

- 正向：举报**他人**动态/评论/用户 → 200，返回 `id`、`targetType`、`targetId`、`createTime`。
- **不能举报自己** → 400 `不能举报自己`（对自己的动态、评论、用户本身都不行）。
- **重复举报相同目标** → 409 `你已经举报过该内容`；**换成不同目标则可再次举报**。
- 目标不存在：动态 → 404 `动态不存在或已被删除`；评论 → 404 `评论不存在或已被删除`；用户 → 404 `用户不存在`。
- 参数：`targetType=4` → 400（**文案是"举报目标类型只能是 1、2 或 3"，不是"举报目标类型不合法"**）；
  `reasonType=6` → 400 `举报原因取值不合法`；缺字段 → 400。
- 未登录 → 401。
- **联动（后台）**：举报提交后应能在 `GET /api/admin/reports?status=0`（待处理）里查到，
  且 `targetPreview` 正确解析出被举报内容摘要。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| `tests/api/test_admin.py::test_report_is_visible_in_admin_queue` | 提交后出现在后台待处理队列 |
| `tests/api/test_admin.py::test_report_list_has_target_preview` | `targetPreview` 解析正确 |
| `tests/api/test_admin.py::test_handle_report_with_delete_action` | 提交 + 后台处置（端到端） |
| `tests/api/test_admin.py::test_handle_report_reject` | 提交 + 后台驳回 |
| `tests/api/test_admin.py::test_handle_report_twice_is_conflict` | 重复处理 409 |

> ⚠️ **覆盖缺口**：`tests/` 下**没有独立的 `test_report.py`**。
> "不能举报自己"、"重复举报 409"、"目标不存在 404"、"参数非法 400" 这些**用户侧**断言
> 目前只由 JUnit 的 `ReportControllerTest`（13 用例）覆盖，
> pytest 仅在 `test_admin.py` 中把举报作为**后台场景的前置数据**来创建。
> 详见 [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)。

JUnit：`ReportControllerTest`（13 用例）、`AdminReportControllerTest`（18 用例）。
