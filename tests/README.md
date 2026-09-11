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
python -m pytest -m smoke          # 只跑冒烟用例
python -m pytest -m read           # 只跑只读用例（不改数据）
python -m pytest api/test_admin.py -v
python -m pytest -k "wildcard"     # 按关键字筛
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
| `api/test_user.py` | 18 | 当前用户、资料修改（含**不能越权改用户名/邮箱/角色**）、改密码全流程、头像、白名单边界 |
| `api/test_follow.py` | 19 | 关注/取消/重复 409/关注自己 400/禁用 423/注销 404、**取消后可重新关注**、关注与粉丝列表、`followedByMe` 回关标识、互相关注、关注流隔离 |
| `api/test_post.py` | 31 | 发布（含图片顺序）、空内容/超长边界/超 9 图/**外链图片被拒**、列表分页与倒序、关注流需登录、详情图片有序、删除权限（作者/他人 403/管理员）、点赞全流程（含**取消后可再次点赞**） |
| `api/test_comment.py` | 13 | 发表、空内容/长度边界、动态不存在、列表正序、删除权限、重复删除 404 |
| `api/test_message.py` | 17 | 发私信自动建会话、未读数只增接收方、标记已读、**会话对称性**、幂等打开、空会话不显示、**游标分页不重不漏**、非参与者 403 |
| `api/test_notification.py` | 16 | 关注/点赞/评论各自产生通知、取消时撤回、**自操作不通知**、单条/全部已读、不能操作他人通知、未读数结构 |
| `api/test_search.py` | 15 | 昵称/用户名匹配、粉丝数倒序、**LIKE 通配符 `%` `_` `\` 转义**、关键词校验、`followedByMe` |
| `api/test_admin.py` | 53 | **访问控制矩阵**（11 个后台接口 × 未登录 401 / 非管理员 403 / 管理员 200）、统计、用户管理（禁用/解禁/不能操作自己）、内容管理、**举报处理（含处置动作与非法组合）**、操作日志 |
| `api/test_message_mutual_follow.py` | 10 | **互关私聊规则**（2026-09-11 冻结）：互关可发、非互关/单向/解除互关 403 `NOT_MUTUAL_FOLLOW`、历史可读、仍可标记已读、重新互关恢复、存量非互关会话可读不可发 |
| `api/test_conversation_mutual_follow.py` | 4 | 会话互关规则：互关可打开、非互关 403、解除互关后历史可读、不可重新打开 |
| `api/test_concurrency.py` | 5 | 并发：重复注册（唯一键兜底）、并发重复关注/点赞（恰好 1 行、计数只 +1）、并发建会话（同一条）、并发发消息（条数=未读=成功数） |
| `api/test_file.py` | 9 | 文件上传安全：正常上传（png/jpg/gif/webp）、空文件、超 5MB、**伪后缀（魔数不符）被拒**、极小文件、未登录 401 |

合计 **229 个用例**。

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

---

## 排查失败用例

先看这两个方向：

1. **是不是种子数据被改动了？** 手工用 curl 或前端操作过之后，
   针对种子数据的绝对值断言会失效。重置数据库即可。
2. **是不是后端没重启？** 改了后端代码但没重新打包，跑的还是旧 jar。
   `mvn -DskipTests package` 后重启。

失败信息里已经带了足够上下文（`ApiResponse` 的 `__repr__` 会打印
HTTP 状态、业务 code 与 message），一般不需要改测试代码去打印中间结果。
