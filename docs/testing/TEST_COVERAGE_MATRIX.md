# 测试覆盖矩阵（TEST_COVERAGE_MATRIX）

一份"接口 / 规则 → 用例"的对照表，用来回答两个问题：
**哪些东西被测了？哪些没测？**

数字口径：**2026-09-14 实测**（`pytest --collect-only` 与 `mvn test` 的输出）。

---

## 1. 总览

| 维度 | 数量 |
|---|---|
| 后端接口 | **50**（16 个 Controller） |
| JUnit 用例 | **291**（21 个测试类） |
| pytest 用例 | **248**（13 个文件） |
| 浏览器回归断言 | **12**（`tests/browser_regression.mjs`，不参与 pytest 收集） |
| 自动化用例合计 | **539**（JUnit + pytest）／ **551**（含浏览器层） |
| 有 pytest 端到端覆盖的接口 | 50 / 50 |
| YAML 登记的核心规则 | 见 `test_cases/` |

> 与旧文档的差异：旧 `README` 写"38 个接口 / 518 个用例 / pytest 229"，
> 均为管理后台扩展前或计划态的陈旧数字。现已按实测更正。
> 2026-09-14 Bug Hunt 修复阶段再 +2（pytest）、+2（JUnit）、+12（浏览器）——
> 见 `bug_report.md`。

---

## 2. 模块级矩阵

| 模块 | 接口数 | pytest 文件 | pytest 用例 | JUnit 测试类 | JUnit 用例 |
|---|---|---|---|---|---|
| 认证 `/api/auth` | 3 | `test_auth.py` | 19 | `AuthControllerTest` | 19 |
| 用户 `/api/users` | 13 | `test_user.py`、`test_follow.py`、`test_search.py` | 20 + 19 + 15 | `UserControllerTest`、`FollowControllerTest`、`UserSearchTest` | 20 + 26 + 16 |
| 动态 `/api/posts` | 7 | `test_post.py` | 31 | `PostControllerTest`、`PostLikeControllerTest` | 31 + 12 |
| 评论 `/api/comments` | 3 | `test_comment.py` | 13 | `CommentControllerTest` | 20 |
| 会话 `/api/conversations` | 4 | `test_conversation_mutual_follow.py`、`test_message.py` | 11 + 17 | `ConversationControllerTest` | 22 |
| 私信 `/api/messages` | 2 | `test_message_mutual_follow.py`、`test_message.py` | 14 + 17 | `MessageControllerTest` | 17 |
| 通知 `/api/notifications` | 4 | `test_notification.py` | 16 | `NotificationControllerTest` | 19 |
| 举报 `/api/reports` | 1 | `test_admin.py`（间接） | — | `ReportControllerTest` | 13 |
| 文件 `/api/files` | 1 | `test_file.py` | **14** | — | — |
| 健康 `/api/health` | 1 | —（`conftest` 探活） | — | `HealthControllerTest` | 4 |
| 管理后台 `/api/admin` | 11 | `test_admin.py` | 53 | `AdminAccessControlTest`、`AdminUserControllerTest`、`AdminContentControllerTest`、`AdminReportControllerTest`、`AdminStatsControllerTest` | 4 + 14 + 13 + 18 + 9 |
| **跨模块** | — | `test_concurrency.py` | **6** | `ResponseSerializationTest`、`EntitySchemaConsistencyTest`、`SeedPasswordTest`、`MiquApplicationTests` | 6 + 2 + 2 + 4 |
| 合计 | **50** | 13 个文件 | **248** | 21 个类 | **291** |

> 前端状态一致性不在本表的接口维度里——它没有对应的接口缺陷。
> 12 项断言见 `tests/browser_regression.mjs`，
> YAML 登记为 `CONC-008` ~ `CONC-011`（`ui_race_rules` 段）。

---

## 3. 冻结业务规则矩阵（最高优先）

### 3.1 互关私聊规则（`frozen_at: 2026-09-11`）

发送侧 —— `api/test_message_mutual_follow.py`（14）

| # | 规则 | pytest | 结果 |
|---|---|---|---|
| 1 | 互关可发送 | `test_mutual_follow_allows_sending` | ✅ |
| 2 | 无关注关系 → 403 | `test_stranger_cannot_send` | ✅ |
| 3 | 单向关注 A→B → 403 | `test_one_way_follow_cannot_send` | ✅ |
| 4 | 单向关注 B→A → 403 | `test_receiver_following_sender_only_cannot_send` | ✅ |
| 5 | 取消互关后 → 403 | `test_cannot_send_after_unfollow` | ✅ |
| 6 | 重新互关后恢复发送 | `test_sending_is_restored_after_refollow` | ✅ |
| 7 | 历史消息仍可读 | `test_history_stays_readable_after_unfollow` | ✅ |
| 8 | 仍可标记已读 | `test_mark_read_still_works_after_unfollow` | ✅ |
| 9 | 被拒请求不留痕（无会话/未读/消息） | `test_blocked_send_leaves_no_trace` | ✅ |
| 10 | DTO 校验优先于互关（空内容 → 400） | `test_dto_validation_precedes_mutual_check` | ✅ |
| 11 | 超长内容 → 400 | `test_mutual_check_precedes_length_check` | ✅ |
| 12 | 给自己发 → 400 | `test_self_check_precedes_mutual_check` | ✅ |
| 13 | 接收者不存在 → 404 | `test_unknown_receiver_precedes_mutual_check` | ✅ |
| 14 | 接收者禁用 → 423 | `test_disabled_receiver_precedes_mutual_check` | ✅ |

会话侧 —— `api/test_conversation_mutual_follow.py`（11）

| # | 规则 | pytest | 结果 |
|---|---|---|---|
| 1 | 互关可打开会话 | `test_mutual_follow_allows_opening` | ✅ |
| 2 | 非互关不能打开 → 403 | `test_stranger_cannot_open_conversation` | ✅ |
| 3 | 单向关注不能打开 → 403 | `test_one_way_follow_cannot_open_conversation` | ✅ |
| 4 | 解除互关后不能重新打开 → 403 | `test_cannot_reopen_after_unfollow` | ✅ |
| 5 | 存量会话解除互关后仍可读 | `test_existing_conversation_stays_readable_after_unfollow` | ✅ |
| 6 | 非参与者读会话 → 403 | `test_non_member_cannot_read_conversation` | ✅ |
| 7 | 非参与者标记已读 → 403 | `test_non_member_cannot_mark_read` | ✅ |
| 8 | 与自己发起会话 → 400 | `test_open_self_precedes_mutual_check` | ✅ |
| 9 | 目标不存在 → 404 | `test_open_unknown_user_precedes_mutual_check` | ✅ |
| 10 | 目标禁用 → 423 | `test_open_disabled_user_precedes_mutual_check` | ✅ |
| 11 | 未登录 → 401 | `test_open_conversation_requires_login` | ✅ |

> **专项合计 25 条**，另有 JUnit `MessageControllerTest`(17) 与
> `ConversationControllerTest`(22) 覆盖同名规则。

### 3.2 关注 / 点赞 / 会话成员

| 规则 | pytest | JUnit | 结果 |
|---|---|---|---|
| 重复关注 → 409 | `test_duplicate_follow_returns_conflict` | `FollowControllerTest` | ✅ |
| 取消后可重新关注 | `test_follow_again_after_unfollow` | `FollowControllerTest` | ✅ |
| 未关注就取关 → 404 | `test_unfollow_without_following` | `FollowControllerTest` | ✅ |
| 不能关注自己 → 400 | `test_cannot_follow_self` | `FollowControllerTest` | ✅ |
| 重复点赞 → 409 | `test_duplicate_like_returns_conflict` | `PostLikeControllerTest` | ✅ |
| 取消后可再次点赞 | `test_like_again_after_unlike` | `PostLikeControllerTest` | ✅ |
| 未点赞就取消 → 404 | `test_unlike_without_like_returns_not_found` | `PostLikeControllerTest` | ✅ |
| A↔B 会话对称（同一条记录） | `test_conversation_is_symmetric` | `ConversationControllerTest` | ✅ |
| 建了没发消息的空会话不出现 | `test_empty_conversation_is_hidden` | `ConversationControllerTest` | ✅ |
| 非成员读消息 → 403 | `test_non_member_cannot_read_messages` | `ConversationControllerTest` | ✅ |
| 不能给自己发消息 → 400 | `test_cannot_message_self` | `MessageControllerTest` | ✅ |

### 3.3 认证与越权

| 规则 | pytest | JUnit | 结果 |
|---|---|---|---|
| 未登录访问受保护接口 → 401 | 各文件均有（如 `test_follow_requires_login`） | `UserControllerTest` | ✅ |
| 禁用后旧 Token 立即失效 | `test_auth.py` | `AuthControllerTest`、`AdminUserControllerTest` | ✅ |
| 不能越权改用户名/邮箱/角色 | `test_update_profile_does_not_change_username_or_email` | `UserControllerTest` | ✅ |
| 后台 11 接口 × 未登录/非管理员/管理员 | `test_admin.py`（`ADMIN_ENDPOINTS`） | `AdminAccessControlTest`(4) | ✅ |
| 不能操作他人通知 | `test_notification.py` | `NotificationControllerTest` | ✅ |

---

## 4. 安全矩阵

| 方向 | 用例 | 结果 |
|---|---|---|
| 上传：正常 4 种格式（png/jpg/gif/webp） | `test_upload_supported_image_types` ×4 | ✅ |
| 上传：URL 可访问（upload→校验→访问 闭环） | `test_uploaded_file_is_reachable` | ✅ |
| 上传：空文件 → 400 `FILE_EMPTY` | `test_empty_file_is_rejected` | ✅ |
| 上传：超 5MB → 400 `FILE_TOO_LARGE` | `test_oversize_file_is_rejected` | ✅ |
| 上传：伪后缀（脚本改名 .jpg）→ 400 | `test_disguised_extension_is_rejected` | ✅ |
| 上传：MIME 与内容不符 → 400 | `test_mime_type_lies_about_content` | ✅ |
| 上传：极小文件（< 12 字节）→ 400 | `test_tiny_file_is_rejected` | ✅ |
| 上传：真图错后缀 → 放行（反向用例） | `test_real_image_with_wrong_extension_is_accepted` | ✅ |
| 上传：未登录 → 401 | `test_upload_requires_login` | ✅ |
| 上传：缺 `file` 字段 → 400 | `test_upload_missing_file_param` | ✅ |
| 上传：非 multipart → 400 | `test_upload_wrong_content_type` | ✅ |
| 搜索：LIKE 通配符 `%` `_` `\` 转义 | `test_search.py` | ✅ |
| 动态：外链图片被拒 | `test_create_post_rejects_external_image_url` | ✅ |
| 越权删除他人内容 → 403 | `test_delete_others_post_is_forbidden` 等 | ✅ |

---

## 5. 并发矩阵

| 场景 | 用例 | 关键断言 | 结果 |
|---|---|---|---|
| 同一用户名并发注册 | `test_concurrent_duplicate_registration` | 恰好 1 成功，其余 409 | ✅ |
| 并发重复关注 | `test_concurrent_duplicate_follow` | 恰好 1 成功；粉丝数只 +1 | ✅ |
| 并发取消关注 | `test_concurrent_unfollow` | 恰好 1 成功；粉丝数归 0 | ✅ |
| 并发重复点赞 | `test_concurrent_duplicate_like` | 恰好 1 成功；likeCount 只 +1 | ✅ |
| 并发建会话 | `test_concurrent_create_conversation` | 全部成功且 id 唯一 | ✅ |
| 并发发消息 | `test_concurrent_send_message` | 全部落库；未读 = 条数 = 8 | ✅ |

对抗措施：每个线程独立 `requests.Session`；用 `threading.Barrier`
把线程压到同一时刻起跑（否则"并发"会退化成串行，测不出竞态）。

---

## 6. 覆盖缺口（如实登记）

| 缺口 | 原因 | 状态 |
|---|---|---|
| 「不能禁用管理员账号」分支 | 校验顺序中"不能操作自己"先命中，且种子只有一个管理员，HTTP 层无输入可触达 | 已知，不做假覆盖（`test_admin.py` 有注释说明） |
| 数十线程同一瞬间首建会话 | 属压测范畴，本次只做到 8 线程 | 未验证 |
| `PUT /api/users/me/avatar` 不校验 `/uploads/` 前缀 | 与动态图片校验不对称（见审计 A/B 条目） | **已修复**（2026-09-14，BUG-005）——pytest + JUnit 各 2 条回归 |
| 真实**多用户**浏览器会话同时操作 | `browser_regression.mjs` 只做单线程时序竞态（人为延迟放大窗口），无并发多浏览器编排 | 未验证 |
| 前端单元/组件测试 | 项目未引入 Vitest 等前端测试框架 | 有意取舍；前端状态层改用 `browser_regression.mjs` 兜底 |
| 种子头像与"相对路径"文档要求不一致 | `data.sql` 的 21 个头像仍是外部图床 `i.pravatar.cc`；校验只作用于写入，存量行不受影响 | 数据侧遗留，不在接口校验范围 |

---

## 7. 一致性核对结论（2026-09-14）

核对 `docs` ↔ `test_cases` ↔ `pytest` ↔ 实际行为，结论：

| 核对项 | 结论 |
|---|---|
| `README` 接口数 38 → 实际 50 | **已更正** |
| `README` 用例数 518 / pytest 229 → 实际 539（JUnit 291 + pytest 248） | **已更正** |
| `tests/README.md` 引用的 4 个文件不存在 | **已补齐**（成为本次新增的 4 个文件） |
| `docs/design.md` §4 接口清单不全 | README 已改指向 `docs/api/README.md`（全量 50 接口的权威文档） |
| `test_cases/` 目录不存在 | **已建立**最小体系（不复制 pytest） |
| `CONV-007`/`CONV-008` 含义冲突 | 原 YAML 不存在、无法复现；已在 `TEST_CASE_SCHEMA.md` §3 定死 `CONV` 与 `MSG` 的分界，**按新规则重新编号，天然无冲突** |
| 「非互关 + 空内容 = 403」旧结论 | **旧结论有误**，实测为 400（DTO 的 `@NotBlank` 先执行），已修正用例与文档 |
| 4 个 docs/testing 文档 + `TESTING_GUIDELINES.md` 缺失 | **已补齐** |
