# Miqu 接口自动化测试（pytest）

通过 **HTTP 打真实运行的后端**，连真实 MySQL。验证的是端到端行为——
序列化、过滤器链、事务提交、SQL 正确性——这些是进程内单元测试覆盖不到的。

---

## 与后端 JUnit 测试的分工

| | `backend/src/test`（JUnit + MockMvc） | `tests/`（pytest） |
|---|---|---|
| 运行方式 | 进程内调用，不起 HTTP | 真实 HTTP 请求 |
| 事务 | `@Transactional` 自动回滚 | **真实提交，不会回滚** |
| 数据库 | 需要，且**必须未被改动** | 需要，但**容忍已有数据** |
| 擅长 | 规则密集的分支覆盖（289 个用例） | 端到端联调、序列化、契约验证 |
| 断言风格 | 可以依赖种子数据的绝对值 | 以**差值**为主，避免依赖全局状态 |

两套并存是有意的：MockMvc 快且能回滚，适合把业务规则钉死；
pytest 慢但真实，能发现"单元测试全绿但联调就挂"的问题（比如序列化格式、CORS、过滤器顺序）。

---

## 运行

```bash
# 1. 先启动后端（默认 8081）
cd backend && mvn spring-boot:run

# 2. 装依赖（首次）
pip install -r tests/requirements.txt

# 3. 跑测试
cd tests && python -m pytest

# 指定后端地址
python -m pytest --base-url http://localhost:8080
# 或
MIQU_BASE_URL=http://localhost:8080 python -m pytest
```

后端没启动时不会得到一堆"连接被拒绝"，而是**直接给出明确提示并退出**
（见 `conftest.py` 里的 `ensure_backend_running`）。

常用筛选：

```bash
python -m pytest -m smoke          # 只跑冒烟用例（43 条）
python -m pytest -m read           # 只跑只读用例（不改数据）
python -m pytest api/test_admin.py -v
python -m pytest -k "wildcard"     # 按关键字筛
python -m pytest api/test_message_mutual_follow.py api/test_conversation_mutual_follow.py -v
```

---

## 两条纪律

### 1. 断言用差值，不用绝对值

写入会真实落库且无法通过接口删除，所以库里的数据会随运行次数增长。
如果写 `assert 用户总数 == 21`，第二次运行就挂了。

```python
# ✗ 依赖全局状态
assert admin_client.get("/api/admin/stats").data["userTotal"] == 21

# ✓ 只断言"这次操作带来的变化"
before = admin_client.get("/api/admin/stats").data["userTotal"]
anonymous.register(f"qa{unique_suffix}")
after = admin_client.get("/api/admin/stats").data["userTotal"]
assert after == before + 1
```

少量针对**种子数据**的绝对值断言是保留的（如 post 1 有 9 张图、test001 关注了 6 个人），
它们只在种子被手工改动后才会失效——那种情况下重置数据库即可。

### 2. 写数据只用当次运行新建的用户

需要写操作时一律用 `fresh_user` / `fresh_users` 夹具，它们每次生成带随机后缀的用户名。
不要去改动种子账号（`test001` / `admin` 等），否则会污染其他用例。

> 用户名必须随机：测试会真实创建用户且**没有删除用户的接口**，
> 固定用户名第二次运行就会撞唯一键。

跑完一轮后库里会多出一些测试用户。需要干净环境时：

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql
```

**好消息**：本套用例是**幂等**的——连跑两次结果一致，无需中途重置。

---

## 覆盖范围

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `api/test_auth.py` | 19 | 注册（成功/重复/大小写归一化/四类参数校验）、登录（成功/密码错/用户不存在/禁用/注销）、**禁用后旧 Token 立即失效**、退出 |
| `api/test_user.py` | 20 | 当前用户、资料修改（含**不能越权改用户名/邮箱/角色**）、改密码全流程、头像（含**外链被拒 / 前缀近似串被拒**）、白名单边界 |
| `api/test_follow.py` | 19 | 关注/取消/重复 409/关注自己 400/禁用 423/注销 404、**取消后可重新关注**、关注与粉丝列表、`followedByMe` 回关标识、互相关注、关注流隔离 |
| `api/test_post.py` | 31 | 发布（含图片顺序）、空内容/超长边界/超 9 图/**外链图片被拒**、列表分页与倒序、关注流需登录、详情图片有序、删除权限（作者/他人 403/管理员）、点赞全流程（含**取消后可再次点赞**） |
| `api/test_comment.py` | 13 | 发表、空内容/长度边界、动态不存在、列表正序、删除权限、重复删除 404 |
| `api/test_message.py` | 17 | 发私信自动建会话、未读数只增接收方、标记已读、**会话对称性**、幂等打开、空会话不显示、**游标分页不重不漏**、非参与者 403 |
| `api/test_notification.py` | 16 | 关注/点赞/评论各自产生通知、取消时撤回、**自操作不通知**、单条/全部已读、不能操作他人通知、未读数结构 |
| `api/test_search.py` | 15 | 昵称/用户名匹配、粉丝数倒序、**LIKE 通配符 `%` `_` `\` 转义**、关键词校验、`followedByMe` |
| `api/test_admin.py` | 53 | **访问控制矩阵**（11 个后台接口 × 未登录 401 / 非管理员 403 / 管理员 200）、统计、用户管理（禁用/解禁/不能操作自己）、内容管理、**举报处理（含处置动作与非法组合）**、操作日志 |
| `api/test_message_mutual_follow.py` | 14 | **互关私聊规则（发送侧，2026-09-11 冻结）**：互关可发、非互关/单向/解除互关 403 `NOT_MUTUAL_FOLLOW`、历史可读、仍可标记已读、重新互关恢复、**被拒请求不留痕**、校验优先级（DTO 400 > 自己 400 > 接收者 404/423 > 互关 403） |
| `api/test_file.py` | 14 | 文件上传安全：正常上传（png/jpg/gif/webp）、**URL 可访问闭环**、空文件、超 5MB、**伪后缀（魔数不符）被拒**、**MIME 与内容不符被拒**、极小文件、真图错后缀放行、未登录 401、缺 `file` 字段 / 非 multipart → 400 |
| `api/test_conversation_mutual_follow.py` | 11 | **会话互关规则**：互关可打开、非互关/单向/解除互关 403、存量会话历史仍可读、**非参与者读会话与标记已读 403**、与自己/不存在/禁用目标的校验优先级、未登录 401 |
| `api/test_concurrency.py` | 6 | 并发：重复注册（唯一键兜底）、并发重复关注/取关（恰好 1 行、计数只 +1）、并发重复点赞（恰好 1 行、likeCount 只 +1）、并发建会话（收敛为同一条）、并发发消息（条数 = 未读 = 成功数） |

合计 **248 个用例**（2026-09-14 实测；其中 246 为基线，新增 2 条为 BUG-005 回归）。

---

## 已知覆盖缺口

**「不能禁用管理员账号」这条分支无法通过 HTTP 触达。**

接口的校验顺序是：
1. 不能操作自己 → `400 CANNOT_OPERATE_SELF`
2. 目标若是管理员 → `403 CANNOT_DISABLE_ADMIN`

种子数据里只有 `admin` 一个管理员，而管理员无法把自己作为禁用目标（第 1 条先命中），
所以第 2 条没有任何输入能走到。

要覆盖它需要第二个管理员账号（在 `data.sql` 里补一个 `admin2`），
或写直接改库的集成测试。这里**不假装覆盖**，明确记为缺口——
`test_disable_self_is_blocked_before_admin_guard` 的注释里有完整说明。

**更高并发（数十线程同一瞬间首建会话）未压测。**
`test_concurrency.py` 只做到 8 线程（已通过）。更高并发属压测范畴，
不做推测性断言（见 `docs/testing/PERFORMANCE_TEST_PLAN.md`）。

**`PUT /api/users/me/avatar` 不校验 `/uploads/` 前缀 —— 已于 2026-09-14 修复。**

原先只校验非空与长度，任意外链都能写进头像，与动态图片的校验不对称。
Bug Hunt 用 API 探针实证复现（`https://evil.example.com/track.png` 返回 200 并落库）后修复：
`UserServiceImpl.updateAvatar` 复用与 `PostServiceImpl.validateImageUrls` 一致的前缀校验。
回归用例：`test_update_avatar_rejects_external_url`、`test_update_avatar_rejects_lookalike_prefix`
（JUnit 侧 2 条）——见 `docs/testing/bug_report.md` BUG-005。

> 遗留（不影响接口校验）：`data.sql` 里 21 个种子用户的头像仍是外部图床
> `https://i.pravatar.cc/...`。校验只作用于写入，存量行不受影响；
> "文档要求相对路径、种子却是外链"这处数据侧不一致依然存在。

**前端无单元/组件测试。** 项目未引入 Vitest 等前端测试框架，属有意取舍；
前端验证靠 `npm run build` + 真实浏览器走查（`docs/testing/TEST_EXECUTION_REPORT.md` §6）。

不过**前端状态层的回归已由 `browser_regression.mjs` 兜住**（见下节）——2026-09-14 的
Bug Hunt 发现：有 3 个真实缺陷（BUG-002/003/004）**pytest 完全测不出来**，
因为后端每次返回都是对的，错在前端没把请求发出去。这类缺陷只能在真实浏览器里回归。

> 完整的缺口登记（含未实现项与原因）见 `test_cases/`（`status: gap` 的条目）
> 与 `docs/testing/TEST_COVERAGE_MATRIX.md` §6。

---

## 三个可选的验证脚本（不参与 pytest）

`api/` 之外还有三个**独立**的验证脚本，用于"不只跑测试，还真的走一遍"的场景。
它们不被 pytest 收集（文件名不是 `test_*.py`），也不进 `requirements.txt`。

### `browser_regression.mjs` —— 前端状态一致性回归（**新增**）

```bash
# 先后端 8081、前端 5173 都要起着
NO_PROXY=localhost,127.0.0.1 node tests/browser_regression.mjs
```

12 项断言，用真实 Chrome 覆盖 **pytest 表达不出来**的那一层：

| 用例 | 覆盖 | 修复前 |
|---|---|---|
| `BUG-001` | 取消关注后关注流不残留对方动态（4 项） | PASS（本就不是缺陷） |
| `BUG-002` | 首页切 tab 后标签与数据一致（3 项） | **FAIL** |
| `BUG-003` | 搜索换关键词后地址栏/输入框/结果一致（3 项） | **FAIL** |
| `BUG-004` | 通知切类型后标签与数据一致（2 项） | **FAIL** |

手法：把首屏请求**人为延迟**，稳定放大"还在飞就点了下一个筛选条件"的竞态窗口。
退出码 0 = 全通过；1 = 有断言失败；2 = 后端/前端没起。

最近一次结果：**12/12 PASS**（2026-09-14，修复后）。修复前为 5 PASS / 6 FAIL。

> 它红**不会**污染 pytest 的绿灯（pytest.ini 的 `testpaths = api`，只收 `test_*.py`）。
> 对应登记见 `test_cases/security_and_concurrency.yaml` 的 `CONC-008` ~ `CONC-011`
> （用 `browser` 指针，校验脚本 `test_cases/check_consistency.py` 会核对指针真实性）。

### `e2e_walkthrough.py` —— 端到端真实链路

```bash
# 需要后端已启动
python e2e_walkthrough.py                       # 默认 http://127.0.0.1:8081
python e2e_walkthrough.py http://localhost:8080 # 指定地址
```

按真实用户流程逐步断言并打印 PASS/FAIL：

```
健康检查 → 注册+登录 → 查看/修改资料 → 修改密码 → 搜索用户 → 关注 → 重复关注 409
→ 回关（互关）→ 打开会话 → 发送私信 → 读取历史 → 标记已读
→ 创建动态 → 点赞 → 重复点赞 409 → 评论
→ 通知（关注/点赞/评论）→ 全部已读
→ 上传图片 → 伪装图片被拒 → 上传 URL 可访问
→ 取消互关 → 发送被拒 403 → 不能重开会话 403 → 历史仍可读 → 仍可标记已读
→ 重新互关 → 恢复发送
→ 管理员登录 → 统计 → 用户管理 → 举报处理 → 未登录访问管理端 401
```

最近一次结果：**41/41 通过**。它断言的是"这条链路真的通"，
与 pytest 的分支断言互补。

### `browser_check.mjs` —— 真实浏览器走查（可选）

```bash
cd frontend && npm run dev     # 先起前端
node tests/browser_check.mjs   # 另开终端
```

用真实 Chrome 打开前端，走 登录 → 首页 → 私聊 → 通知 → 搜索 → 个人中心，
并收集**控制台报错**与**未捕获 JS 异常**（这两样正是"白屏 / 按钮没反应"的信号）。

前置条件：本机有 Chrome，且能加载到 `playwright`（`npm i -D playwright`）。
截图输出到 `.workbuddy/screenshots/`。

最近一次结果：**11/12**。唯一"失败"项是外部头像图床
`i.pravatar.cc` 被网络策略拦截，属环境问题而非代码缺陷。

---

## 排查失败用例

先看这两个方向：

1. **是不是种子数据被改动了？** 手工用 curl 或前端操作过之后，
   针对种子数据的绝对值断言会失效。重置数据库即可。
2. **是不是后端没重启？** 改了后端代码但没重新打包，跑的还是旧 jar。
   `mvn -DskipTests package` 后重启。

失败信息里已经带了足够上下文（`ApiResponse` 的 `__repr__` 会打印
HTTP 状态、业务 code 与 message），一般不需要改测试代码去打印中间结果。
