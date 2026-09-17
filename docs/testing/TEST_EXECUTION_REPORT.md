# Miqu 测试执行报告

> 本文件中的**所有数字均为实际执行结果**，无估算、无补充。
> 复现命令见文末「复现步骤」。

---

## 1. 测试时间与环境

| 项 | 值 |
|---|---|
| 执行日期 | 2026-09-14 |
| 操作系统 | Windows (win32) |
| JDK | Java 21.0.11（`JAVA_HOME=D:\soft\java\Java\jdk-21`） |
| 构建工具 | Apache Maven 3.9.14 |
| 后端 | Spring Boot 3.4.7，`miqu-backend-1.0.0.jar`，监听 **8081** |
| 数据库 | MySQL 9.4（本机 3306），库名 `miqu` |
| Python | 3.12.10 |
| pytest | 9.1.1（pytest / pluggy 1.6.0） |
| requests | 2.34.2 |
| Node / 前端 | Node 22.22.2，Vue 3.5 + Vite 6，dev server 5173 |
| 浏览器（前端验证） | 本机 Chrome（Playwright `channel: chrome`，headless） |

**后端启动自检**

```json
GET /api/health  ->  {"code":200,"message":"success",
                      "data":{"status":"UP","application":"miqu","database":"UP"}}
```

---

## 2. 测试数量与结果

### 2.1 pytest —— 接口自动化（真实 HTTP + 真实数据库）

```
Pytest:
250 collected
250 passed
0 failed
0 skipped
用时：约 39 秒
```

连续执行**两次**，两次均为 `250 passed`（套件幂等，中途无需重置数据库）。

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `api/test_admin.py` | 53 | 后台访问控制矩阵、统计、用户/内容管理、举报处置、操作日志 |
| `api/test_post.py` | 31 | 发布与边界、列表分页、删除权限、点赞全流程 |
| `api/test_follow.py` | 19 | 关注/取关/重复/自关注/禁用/注销、列表、回关标识 |
| `api/test_auth.py` | 19 | 注册校验、登录各失败分支、禁用后旧 Token 失效 |
| **`api/test_user.py`** | **20** | 资料与密码修改、越权防护、白名单边界、**头像外链与近似前缀被拒（BUG-005 回归）** |
| `api/test_message.py` | 17 | 发私信建会话、未读、标记已读、游标分页、非成员 403 |
| `api/test_notification.py` | 16 | 三类通知产生与撤回、自操作不通知、已读状态 |
| `api/test_search.py` | 15 | 昵称/用户名匹配、粉丝数倒序、LIKE 通配符转义 |
| **`api/test_message_mutual_follow.py`** | **14** | **互关私聊规则（发送侧）** |
| **`api/test_file.py`** | **16** | **文件上传安全：魔数、大小、MIME、空/极小文件、可访问性**；**缺失静态资源真 404** |
| `api/test_comment.py` | 13 | 发表、长度与空内容、删除权限 |
| **`api/test_conversation_mutual_follow.py`** | **11** | **互关私聊规则（会话打开侧）+ 参与者隔离** |
| **`api/test_concurrency.py`** | **6** | **并发注册/关注/取关/点赞/建会话/发消息** |
| 合计 | **250** | |

> 两个阶段叠加：2026-09-14 收尾阶段新增 4 个文件 45 条（201 → 246）；
> 同日 Bug Hunt 修复阶段新增 `test_user.py` 2 条 BUG-005 回归（246 → 248）；
> 2026-09-16 头像修复阶段新增 `test_file.py` 2 条 BUG-007 回归（248 → 250）。

### 2.2 JUnit —— 进程内 MockMvc

```
Tests run: 291, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

执行前已将数据库重置为 `schema.sql` + `data.sql` 的规范种子状态
（JUnit 断言依赖种子数据的绝对值，与 pytest 的差值断言策略不同）。

> 289 → 291：`UserControllerTest` 新增 2 条 BUG-005 回归
> （`updateAvatar_externalUrl_rejected` / `updateAvatar_lookalikePrefix_rejected`）。

### 2.3 两套测试合计

```
250 (pytest) + 291 (JUnit) = 541 条自动化用例
```

### 2.4 浏览器回归 —— 前端状态一致性（真实 Chrome）

```
15 项断言 / 15 PASS / 0 FAIL
exit code 0
```

`tests/browser_regression.mjs`，覆盖 **pytest 表达不出来**的那一层：
BUG-002/003/004 三个前端缺陷的回归 + BUG-001 的固定守卫。

修复前为 **5 PASS / 6 FAIL**（BUG-002/003/004 的准入闸门），修复后 15/15。

> 它不参与 pytest 收集（`pytest.ini` 的 `testpaths = api`，只收 `test_*.py`），
> 所以它红不会污染上面 250 条的绿灯。

---

## 3. 核心业务覆盖

### 3.1 互关私聊规则（2026-09-11 冻结）

**专项用例 25 条**（pytest）；另有 JUnit `MessageControllerTest` / `ConversationControllerTest`。

| 规则 | 断言结果 |
|---|---|
| 互关可发送 | ✅ |
| 非互关（无关系）不能发送 → 403 `NOT_MUTUAL_FOLLOW` | ✅ |
| 单向关注（A→B）不能发送 | ✅ |
| 单向关注（B→A）不能发送 | ✅ |
| 取消互关后不能发送 | ✅ |
| 取消互关后**历史消息仍可读取** | ✅ |
| 取消互关后仍可标记已读 | ✅ |
| 重新互关后恢复发送 | ✅ |
| 被拒的发送**不产生会话 / 不产生未读 / 不落消息** | ✅ |
| 互关可打开会话；非互关 / 解除互关后不能打开 | ✅ |
| 非参与者读会话 / 标记已读 → 403 `NOT_CONVERSATION_MEMBER` | ✅ |
| 给自己发 / 不存在用户 / 已禁用用户 的校验优先级 | ✅ |

**校验优先级（实测确定，非推测）**

```
① DTO Bean Validation（content 为空 / 超长）→ 400
② Service 内：不能给自己 → 400
③ 接收者不存在/已注销 → 404，已禁用 → 423
④ 互关校验 → 403 NOT_MUTUAL_FOLLOW
```

> 注：早前文档曾记「非互关 + 空内容 = 403」。实测为 **400**——
> DTO 的 `@NotBlank` 先于 Service 执行。已在用例中固化为
> `test_dto_validation_precedes_mutual_check`。

### 3.2 用户 / 关注 / 动态 / 评论 / 通知 / 搜索 / 管理

全部为核心链路，pytest 与 JUnit 双侧覆盖，结果均为 ✅。

### 3.3 端到端真实链路（非 pytest，直接打后端）

独立脚本按真实用户流程逐步断言，**41/41 通过**：

```
健康检查 ✅  注册+登录 ✅  查看资料 ✅  修改资料 ✅  修改密码 ✅
搜索用户 ✅  关注 ✅  重复关注 409 ✅  关注列表 ✅  回关（互关）✅
互关标识 ✅  打开会话 ✅  发送私信 ✅  会话列表 ✅  读取历史 ✅
未读数 ✅  标记已读 ✅  创建动态 ✅  动态列表 ✅  点赞 ✅
重复点赞 409 ✅  发表评论 ✅  计数正确 ✅
通知覆盖关注/点赞/评论 ✅  通知未读与全部已读 ✅
上传图片 ✅  伪装图片被拒 ✅  上传 URL 可访问 ✅
取消互关 ✅  取消后发送被拒 403 ✅  取消后不能重开会话 403 ✅
取消后历史可读 ✅  取消后仍可标记已读 ✅  重新互关恢复发送 ✅
管理员登录 ✅  管理端统计 ✅  用户管理 ✅  举报处理 ✅
未登录访问管理端 401 ✅
```

---

## 4. 安全测试

| 方向 | 结论 |
|---|---|
| 上传：按魔数判定类型 | ✅ 改后缀 / 改 MIME 均被拒 |
| 上传：大小上限 5MB | ✅ 超限 400 `FILE_TOO_LARGE` |
| 上传：空文件 / 极小文件（< 12 字节） | ✅ 400 |
| 上传：UUID 重命名，不回显原始文件名 | ✅ |
| 上传：非 multipart / 缺 `file` 字段 | ✅ 400（本次修复，原为 500） |
| 未登录访问受保护接口 | ✅ `/api/**` 默认失败关闭，401 |
| 越权修改（用户名 / 邮箱 / 角色） | ✅ 不可改 |
| 配置 / 权限矩阵（11 个后台接口 × 未登录 / 非管理员 / 管理员） | ✅ |
| SQL 注入（LIKE 通配符 `%` `_` `\` 转义） | ✅ |
| 目录穿越（存储路径双重校验 + 文件名不参与路径拼接） | ✅ 代码层实现，用例覆盖「不回显原始文件名」 |

---

## 5. 并发测试

| 场景 | 结果 |
|---|---|
| 同一用户名并发注册（8 线程） | 恰好 1 成功，其余 409 |
| 同一用户并发关注同一目标（8 线程） | 恰好 1 成功，其余 409，粉丝数只 +1 |
| 并发取消关注（8 线程） | 恰好 1 成功，其余 404，粉丝数归 0 |
| 同一用户并发点赞同一动态（8 线程） | 恰好 1 成功，其余 409，likeCount 只 +1 |
| 并发创建同一会话（8 线程） | 全部成功且**收敛为同一个会话 id** |
| 并发发送消息（8 线程） | 8 条全部落库，未读数 = 消息条数 = 8 |

### 本次由并发用例发现并修复的缺陷

**并发首次建会话 / 首发消息会大量失败。**

- **现象**：8 个并发请求首次向同一接收者发消息，4 条返回 409 `资源冲突`。
- **根因**：`ConversationServiceImpl.getOrCreateEntity()` 捕获
  `DuplicateKeyException` 后，在**同一事务**内用普通 `SELECT` 重查。
  MySQL 默认 REPEATABLE READ，本事务的一致性读快照在更早的
  `requireActiveUser()` 查用户时就已经建立，因此看不到并发事务
  刚提交的那条会话 → 重查仍为 null → 异常继续上抛。
- **走弯路**：先尝试 `SELECT ... FOR UPDATE`（当前读），结果 8 个事务在
  同一 gap 上加锁**直接死锁**（`MySQLTransactionRollbackException`）。
- **修复**：改用 `REQUIRES_NEW` 新事务重查——新事务拥有全新 read view，
  一定能读到已提交记录。见 `ConversationServiceImpl` 的冲突分支注释。
- **修复后**：上述 6 个并发场景全部通过。

---

## 6. 前端实际验证

| 项 | 结果 |
|---|---|
| `npm run build` | ✅ 成功，无编译错误，所有视图 chunk 正常产出 |
| dev server（5173） | ✅ 可访问，SPA 路由与 `/src/main.ts` 正常 |
| 代理 `/api` → 后端 8081 | ✅ 返回真实数据 |
| 登录页 | ✅ 正常渲染，无白屏，演示账号入口存在 |
| 登录（test001 / 123456） | ✅ 成功跳转首页 |
| 首页 / 私聊 / 通知 / 搜索 / 个人中心 / 注册页 | ✅ 均有真实内容渲染 |
| 未捕获 JS 异常 | ✅ 0 |
| 控制台报错 | ⚠️ 仅外部头像图床（见下方已知限制） |

---

## 7. 已知限制

以下为**如实记录**的已知问题，均不影响交付与演示。

1. **演示头像依赖外部图床**。`data.sql` 的 21 个头像使用
   `https://i.pravatar.cc/...`。无外网时头像显示为破图（页面其余部分正常）。
   控制台可见 `ERR_BLOCKED_BY_RESPONSE.NotSameOrigin @ https://i.pravatar.cc/...`，
   与业务代码无关。若要完全离线演示，可换成 `backend/uploads/` 下的本地占位图。
2. **并发创建会话的极端场景**：`getOrCreateEntity` 在应用层"先查再插"，
   由数据库唯一键兜底，冲突时开新事务重查。8 线程压测已通过；
   更高并发（数十线程同一瞬间首建）未做压测，属于未验证区域。
3. **`uk_users` 冲突的日志文案**：`GlobalExceptionHandler` 把
   `uk_users` 映射为通用 `CONFLICT(409)`，并在日志里打 "未映射的唯一约束冲突"。
   功能正确，但文案具有误导性（`uk_users` 其实是已登记的约束）。
4. **`PUT /api/users/me/avatar` 不校验 `/uploads/` 前缀**，与动态图片校验不对称
   （见 `docs/testing/API_DOCUMENT_AUDIT.md`）。属既有设计缺口，本次未改。
5. **「不能禁用管理员账号」分支无法通过 HTTP 触达**：
   校验顺序里"不能操作自己"先命中，而种子数据只有一个管理员。已记为覆盖缺口，
   不做假覆盖（详见 `tests/README.md`）。
6. **`test_cases/` 为本次新建的最小体系**：登记冻结规则与高价值用例，
   与 pytest **不做 1:1 复制**（避免维护两套真相）。详见 `test_cases/README.md`。

---

## 8. 复现步骤

```bash
# ---- 1. 重置数据库（会清空 miqu 库）----
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql

# ---- 2. JUnit（289）----
cd backend && mvn test

# ---- 3. 启动后端 ----
java -jar backend/target/miqu-backend-1.0.0.jar --server.port=8081
curl http://localhost:8081/api/health

# ---- 4. pytest（250）----
pip install -r tests/requirements.txt
cd tests && python -m pytest --collect-only -q
python -m pytest -q

# ---- 4. 端到端真实链路（41 项）----
cd tests && python e2e_walkthrough.py http://127.0.0.1:8081

# ---- 5. 前端 ----
cd frontend && npm install && npm run dev      # http://localhost:5173
node tests/browser_check.mjs                   # 页面走查（可选）
node tests/browser_regression.mjs              # 前端状态一致性回归（12 项，可选但建议跑）
```

> ⚠️ 端口说明：本机 8080 被 Windows 服务（`ApplicationWebServer.exe`）占用，
> 因此后端默认 **8081**。换机器可用 `SERVER_PORT=8080` 覆盖。
>
> ⚠️ Vite 6 只监听 IPv6：前端地址必须用 `http://localhost:5173`，
> 访问 `http://127.0.0.1:5173` 会连不上。

---

## 9. 结论

```
Pytest:   250 collected / 250 passed / 0 failed
JUnit:    291 run       /   0 failures / 0 errors / 0 skipped
合计:     541 条自动化用例，0 失败
浏览器:   15 项断言 / 15 PASS（前端状态一致性 + 图片降级回归）
端到端:   41/41 通过
前端:     vue-tsc 类型检查通过、构建通过、页面渲染正常

Status: PASS
```

### 9.1 已修复的真缺陷

**2026-09-14 Bug Hunt 修复（4 个）**

| ID | 缺陷 | 级别 | 回归 |
|---|---|---|---|
| BUG-002 | 首页切 tab 后标签与数据不一致 | P2 | `BUG-002.a~c` |
| BUG-003 | 搜索换关键词后地址栏与结果不一致 | P2 | `BUG-003.a~c` |
| BUG-004 | 通知切类型后标签与数据不一致 | P2 | `BUG-004.a~b` |
| BUG-005 | 头像可写入任意外链（不校验 `/uploads/`） | P2 | pytest + JUnit 各 2 条 |

**2026-09-16 头像问题修复（3 个，其中 1 个待决策）**

| ID | 缺陷 | 级别 | 回归 |
|---|---|---|---|
| BUG-006 | 头像加载失败无兜底，取不到就是永久破图（全站 15 处共用组件） | **P1** | `BUG-006.a~c` |
| BUG-007 | 静态资源 404 被包成 `HTTP 200 + JSON`，`<img>` 解码失败 | P2 | pytest 2 条 |
| BUG-008 | 头像接口不校验目标文件是否存在 | P2 | ⏸ 待决策（与 2 条既有用例冲突） |

BUG-001（取消关注后仍能看到对方动态）经五路交叉验证为 **NOT_A_BUG / 未复现**，
已固化为长期回归守卫 `BUG-001.a~d`。完整证据链见
[`bug_report.md`](bug_report.md)；头像问题的完整诊断见
[`avatar-issue-diagnosis.md`](avatar-issue-diagnosis.md)。
