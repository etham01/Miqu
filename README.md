# Miqu「觅取」社交系统

一个用于 **Java 后端实践 / Spring Boot 实践 / MySQL 设计实践 / 前后端分离 / 接口自动化测试**
的中小型社交平台。功能完整、结构清晰、接口规范、**优先考虑可测试性**。

> 设计文档见 [`docs/design.md`](docs/design.md)（系统架构、ER 设计、业务流程、API 清单、22 条设计问题）
> 本文档说明如何把项目跑起来，以及目前的实现进度。

---

## 一、技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.4.7 · MyBatis-Plus 3.5.7 · MySQL 8/9 · Maven |
| 认证 | JWT（jjwt 0.12.6，HS256）· BCrypt |
| 文档 | SpringDoc OpenAPI 2.8.6（Swagger UI） |
| 前端 | Vue 3 · Vite · TypeScript · Element Plus · Pinia · Axios（待实现） |
| 测试 | JUnit 5 · MockMvc · pytest（后续阶段） |

---

## 二、目录结构

```
miqu/
├── README.md
├── docs/
│   └── design.md            # 设计文档（先读这个）
├── database/
│   ├── schema.sql           # 建库 + 11 张表 + 索引 + 约束
│   └── data.sql             # 初始化数据（固定主键 ID，可重复断言）
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/miqu/
│       │   ├── common/      # Result / PageResult / ErrorCode / BizException / 枚举 / 常量
│       │   ├── config/      # MyBatis-Plus / Jackson / OpenAPI / WebMvc / 配置绑定
│       │   ├── security/    # JWT 签发解析 / 认证过滤器 / 访问控制拦截器
│       │   ├── exception/   # GlobalExceptionHandler
│       │   ├── entity/ mapper/ dto/ vo/ converter/
│       │   ├── service/     # 接口 + impl（含 admin/ 子包）
│       │   └── controller/  # 含 admin/ 子包
│       ├── main/resources/  # application.yml / logback-spring.xml
│       └── test/java/com/miqu/   # 291 个 JUnit 用例
├── frontend/                # Vue 3 + Vite + TypeScript + Element Plus
│   └── src/
│       ├── api/             # Axios 实例 + 按模块拆分的接口封装 + 与后端对齐的 TS 类型
│       ├── components/      # PostCard / PostEditor / CommentList / FollowButton / …
│       ├── layouts/         # DefaultLayout（前台）/ AdminLayout（后台）
│       ├── router/          # 路由表 + 登录与管理员守卫
│       ├── stores/          # Pinia：登录态、未读角标（轮询）
│       ├── styles/          # 设计令牌 + 全局样式 + Element Plus 主题覆盖
│       ├── utils/           # 时间格式化等
│       └── views/           # 页面（auth / home / search / user / post / message /
│                            #      notification / profile / admin）
└── tests/                   # pytest 接口自动化（250 个用例）
    ├── conftest.py          # 夹具：后端探活、登录态、随机用户工厂
    ├── api/                 # 13 个用例文件，按模块拆分
    ├── browser_regression.mjs  # 前端状态一致性回归（真实 Chrome，15 项断言）
    └── utils/client.py      # HTTP 封装（统一响应体解析）

test_cases/                  # YAML 测试用例（设计与管理文档，不参与执行）
docs/                        # 设计文档 + 接口文档 + 测试文档
├── design.md                # 系统设计（架构 / ER / 业务流程）
├── TESTING_GUIDELINES.md    # 测试编写规范（动手前先读）
├── api/                     # 50 个接口的完整文档
└── testing/                 # 测试体系 / 用例规范 / 覆盖矩阵 / 执行报告 / 压测方案
```

---

## 三、环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+（本项目在 **MySQL 9.4** 上验证通过）

---

## 四、快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql
```

> ⚠️ `schema.sql` 第一行是 `DROP DATABASE IF EXISTS miqu`，会**清空**已存在的 `miqu` 库。
> 需要重置环境时，重新执行这两个脚本即可。

### 2. 启动后端

**首次**需要把你的数据库口令填进本地配置（这个文件不会被提交）：

```bash
cd backend
cp application-local.yml.example src/main/resources/application-local.yml
# 然后编辑 src/main/resources/application-local.yml，改掉 password
```

之后就不需要任何额外配置了，`local` profile 默认激活：

```bash
# IDEA：直接点 Run 即可（不需要配 Run Configuration 的环境变量）

# 命令行
cd backend && mvn spring-boot:run

# 或运行 jar
mvn -DskipTests package
java -jar target/miqu-backend-1.0.0.jar
```

**临时换口令不用改文件**，环境变量优先级更高：

```bash
# Git Bash
DB_PASSWORD=别的口令 mvn spring-boot:run

# PowerShell
$env:DB_PASSWORD="别的口令"; mvn spring-boot:run
```

#### 凭据是怎么管的

| 文件 | 是否提交 | 放什么 |
|---|---|---|
| `application.yml` | ✅ 提交 | 结构配置 + `${DB_PASSWORD:}` 占位符（**不含真实口令**） |
| `application-local.yml` | ❌ 已 gitignore | 你本机的数据库口令 |
| `application-local.yml.example` | ✅ 提交 | 给新机器的模板 |

这样"IDEA 里点 Run 就能用"和"口令不进仓库"可以同时成立。
不加配置直接跑会怎样？启动时会明确告诉你缺什么（见「常见启动问题」）。

启动后：

| 地址 | 说明 |
|---|---|
| http://localhost:8081/api/health | 健康检查（免登录） |
| http://localhost:8081/swagger-ui.html | Swagger UI，点右上角 Authorize 填 token 后可调试 |

> **端口说明**：默认端口是 **8081** 而不是惯例的 8080。
> 因为本机上 8080 已被 Windows 服务 `ApplicationWebServer.exe` 长期占用，用 8080 会直接启动失败。
> 换机器后可用 `SERVER_PORT=8080` 或 `--server.port=8080` 覆盖。

#### 内存吃紧的机器上启动

Spring Boot 默认会按物理内存比例预留堆，在开发机上常常一个空服务就占 700~800MB。
如果机器内存紧张（IDEA + 浏览器 + 微信这些加起来轻松吃掉 10GB），
可以用下面的参数把占用压到 **200MB 以内**，这个项目的体量完全够用：

```bash
java -Xms64m -Xmx320m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC \
     -jar target/miqu-backend-1.0.0.jar
```

`mvn spring-boot:run` 也支持，加 `-Dspring-boot.run.jvmArguments="..."` 即可。

### 3. 启动前端

```bash
cd frontend
npm install          # 首次
npm run dev          # 开发服务器，默认 http://localhost:5173
```

打开 http://localhost:5173 即可。前端通过 Vite 代理把 `/api` 与 `/uploads`
转发到 `http://localhost:8081`，因此**不涉及 CORS**，代理目标在 `vite.config.ts` 里改。

生产构建：

```bash
npm run build        # 产物在 dist/
npm run type-check   # 只做类型检查，不产出文件
```

> 登录页上有三个「演示账号」按钮（普通用户 / 管理员 / 被禁用），点一下自动填入，
> 便于直接体验不同角色的界面差异。

---

## 常见启动问题

### 前端显示「系统异常，请稍后重试」

**九成是后端连不上数据库**，而不是前端或接口本身的问题。

后端启动时会主动验证数据库连接，连不上会**直接失败并打印修复方法**：

```
============================================================
 启动失败：无法连接数据库
------------------------------------------------------------
 当前配置
   地址    jdbc:mysql://localhost:3306/miqu?...
   用户名  root
 失败原因
   Access denied for user 'root'@'localhost' (using password: NO)
------------------------------------------------------------
 请依次检查
 1) MySQL 是否已启动
 2) 是否提供了数据库口令 ...
============================================================
```

看到 `using password: NO` 就是**根本没传口令**——通常是没建 `application-local.yml`。
按第 2 步的说明补上即可（也可以临时用环境变量顶上）。

> 启动自检是刻意加的：Hikari 连接池**懒初始化**，不检查的话应用会"启动成功"，
> 然后每个请求都 500，而日志里的真实原因和前端看到的报错完全对不上。
> 这是本项目真实踩过的坑，详见 `HealthControllerTest` 的注释。

### 前端显示「网络异常，请检查后端服务是否已启动」

后端进程没起来，或者端口不是 8081。先确认：

```bash
curl http://localhost:8081/api/health
```

### 健康检查怎么判断

`GET /api/health` **会实际探测数据库**，不只是报告进程存活：

```json
// 正常
{"code":200,"message":"success","data":{"status":"UP","application":"miqu","database":"UP"}}

// 数据库不可用
{"code":500,"message":"数据库不可用，服务暂时无法提供业务功能",
 "data":{"status":"DOWN","application":"miqu","database":"DOWN"}}
```

`database` 字段是刻意加的：进程活着不等于能提供服务。
只报进程存活的话，数据库挂掉时会出现"健康检查 UP，但每个业务请求都 500"这种
极具误导性的状态——这正是本项目真实踩过的坑。

### 上线前请确认在 `backend/` 目录下启动

`uploads/`（上传文件）与 `logs/` 都是相对路径，相对的是**当前工作目录**：

```bash
cd backend            # ← 必须先切进来
mvn spring-boot:run
```

如果在项目根目录直接跑 `java -jar backend/target/xxx.jar`，日志与上传文件会落到
项目根目录下。功能仍然正常（读写用的是同一个基准路径），但会和预期位置不一致。

---

## 五、测试账号

所有种子账号密码均为 `123456`。

| 用户名 | 密码 | 说明 |
|---|---|---|
| `admin` | `123456` | 管理员（role=2） |
| `test001` ~ `test018` | `123456` | 普通用户，`test001` 是 id=2 的主测试账号 |
| `banned001` | `123456` | 已被禁用，登录返回 423 —— 用于验证禁用逻辑 |
| `deleted001` | `123456` | 已注销，登录返回 401 —— 用于验证逻辑删除 |

种子数据中几个**可稳定断言**的事实：

| 事实 | 值 |
|---|---|
| `post` id=1 的点赞数 / 评论数 / 图片数 | 10 / 5 / 9（9 张是需求上限） |
| `post` id=2 的图片数 | 0（边界：无图动态） |
| `post` id=3 的内容长度 | 1000 字符（边界：内容上限） |
| `user` id=2 ↔ id=3 | 互相关注 |
| `user` id=2 的粉丝数 | 14 |

批量数据用确定性的 `INSERT ... SELECT` 生成（**不用随机数**），因此每次初始化的结果完全一致，测试可以直接断言数量。

---

## 六、接口速查

统一响应格式：

```json
{ "code": 200, "message": "success", "data": {} }
```

错误码与 HTTP 语义对齐：`400` 参数错误 · `401` 未登录 · `403` 无权限 · `404` 不存在 ·
`409` 冲突（重复注册/关注/点赞）· `423` 账号禁用 · `500` 系统异常。

> 所有 `id` 字段序列化为**字符串**，避免前端 JS 的 2^53 精度问题。

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册 | — |
| POST | `/api/auth/login` | 登录 | — |
| POST | `/api/auth/logout` | 退出 | 🔒 |
| GET | `/api/users/me` | 当前用户信息 | 🔒 |
| PUT | `/api/users/me` | 修改资料（昵称/性别/生日/简介） | 🔒 |
| PUT | `/api/users/me/password` | 修改密码 | 🔒 |
| PUT | `/api/users/me/avatar` | 修改头像 | 🔒 |
| GET | `/api/users/me/following` | 我的关注 | 🔒 |
| GET | `/api/users/me/followers` | 我的粉丝 | 🔒 |
| GET | `/api/users/{id}` | 用户主页（含关注状态） | — |
| GET | `/api/users/{id}/posts` | 某用户的动态 | — |
| GET | `/api/users/{id}/following` | 某用户关注的人 | — |
| GET | `/api/users/{id}/followers` | 某用户的粉丝 | — |
| POST | `/api/users/{id}/follow` | 关注 | 🔒 |
| DELETE | `/api/users/{id}/follow` | 取消关注（未关注→404） | 🔒 |
| POST | `/api/posts` | 发布动态 | 🔒 |
| GET | `/api/posts` | 首页动态流 `?tab=latest\|following` | — |
| GET | `/api/posts/{id}` | 动态详情 | — |
| DELETE | `/api/posts/{id}` | 删除动态（作者或管理员） | 🔒 |
| GET | `/api/posts/{id}/likes` | 点赞用户列表 | — |
| POST | `/api/posts/{id}/like` | 点赞（重复→409） | 🔒 |
| DELETE | `/api/posts/{id}/like` | 取消点赞（未点赞→404） | 🔒 |
| POST | `/api/posts/{id}/comments` | 发表评论 | 🔒 |
| GET | `/api/posts/{id}/comments` | 评论列表（时间正序） | — |
| DELETE | `/api/comments/{id}` | 删除评论（作者或管理员） | 🔒 |
| GET | `/api/conversations` | 会话列表（只有消息的会话） | 🔒 |
| POST | `/api/conversations` | 获取或创建会话（幂等） | 🔒 |
| GET | `/api/conversations/{id}/messages` | 聊天记录（游标 `beforeId`） | 🔒 |
| PUT | `/api/conversations/{id}/read` | 标记会话已读 | 🔒 |
| POST | `/api/messages` | 发送私信（自动建会话） | 🔒 |
| GET | `/api/messages/unread-count` | 私信未读总数 | 🔒 |
| GET | `/api/notifications` | 通知列表（可按类型/已读过滤） | 🔒 |
| GET | `/api/notifications/unread-count` | 通知未读数（分类） | 🔒 |
| PUT | `/api/notifications/{id}/read` | 单条标记已读 | 🔒 |
| PUT | `/api/notifications/read-all` | 全部标记已读 | 🔒 |
| GET | `/api/users/search` | 搜索用户（昵称/用户名） | — |
| POST | `/api/reports` | 提交举报 | 🔒 |
| POST | `/api/files/image` | 上传图片（字段名 `file`，≤5MB） | 🔒 |
| GET | `/api/health` | 健康检查 | — |

### 管理后台（全部需要管理员 👑）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/stats` | 用户/动态/评论总数、今日新增、待处理举报数 |
| GET | `/api/admin/users` | 用户列表（关键词 + 状态过滤） |
| GET | `/api/admin/users/{id}` | 用户详情 |
| PUT | `/api/admin/users/{id}/status` | 禁用 / 解禁（不能操作自己） |
| GET | `/api/admin/posts` | 动态列表（作者 + 内容关键词过滤） |
| DELETE | `/api/admin/posts/{id}` | 删除动态（级联清理） |
| GET | `/api/admin/comments` | 评论列表 |
| DELETE | `/api/admin/comments/{id}` | 删除评论 |
| GET | `/api/admin/reports` | 举报列表（带被举报对象摘要） |
| PUT | `/api/admin/reports/{id}/handle` | 处理举报，可同时执行处置动作 |
| GET | `/api/admin/logs` | 管理员操作日志（只读） |

> 后台权限是**两层**的：`/api/admin/**` 不在免登录白名单里，未登录会被拦成 401；
> 登录了但不是管理员则由 `@RequireAdmin` 拦成 403。两种失败**文案不同**，
> 前端才能判断该引导登录还是提示无权限。

### 序列化约定（前端与测试都依赖）

- **id 字段是字符串**：`{"id":"2"}` —— JS 的 Number 安全上限是 2^53，防将来换雪花 ID 时静默丢精度
- **计数类字段是数字**：`{"likeCount":10}`、`{"total":15}` —— 要参与前端运算
- **时间是格式化字符串**：`"2026-09-11 10:00:00"`，不是时间戳数组

这条边界容易被无意破坏：用 `Map<String, Long>` 返回计数时，自动装箱后的 `Long` 会被当成 id 一样转成字符串。
`ResponseSerializationTest` 专门守住它（曾经真的错过一次）。

> 白名单用 `{id:[0-9]+}` 而不是 `*`：否则 `/api/users/me` 会被一并放行，
> 个人中心就变成免登录可访问了。`/api/posts?tab=following` 虽然路径在白名单里，
> 但未登录会被 Service 拦下返回 401。

完整接口清单见 **[`docs/api/README.md`](docs/api/README.md)**（50 个接口，以生产代码为唯一事实来源反向整理，
含通用约定、各模块字段与错误码）。`docs/design.md` 第 4 节是**设计阶段**的接口清单，
两者口径不同，以 `docs/api/` 为准。

---

## 六点五、核心业务规则（演示与验收要点）

Miqu 有几条**容易被忽略、但已被自动化测试钉死**的业务规则，演示与验收时重点关注：

1. **私信需要双方互相关注**（2026-09-11 冻结）
   - 发送消息 `POST /api/messages` 与打开会话 `POST /api/conversations` 都要求 `A 关注 B 且 B 关注 A`；
   - 非互关返回业务码 `403` / `NOT_MUTUAL_FOLLOW`（文案"需要互相关注后才能私聊"）；
   - **历史会话不受影响**：解除互关后仍可读取历史消息、仍可标记已读，只是不能发送新消息、不能重新打开会话；
   - **重新互关后自动恢复**发送能力（实时判断，不新增状态字段）；**管理员同样受约束**。
2. **关系表物理删除、业务主表逻辑删除**：取消关注 / 取消点赞 / 动态图片是物理 `DELETE`（否则唯一键会被软删行占用，导致"取消后永远无法再次关注/点赞"）；动态与评论是逻辑删除。
3. **白名单"失败关闭"**：不在白名单里的 `/api/**` 一律要求登录；`/api/users/me` 刻意不在白名单内。
4. **统一响应与序列化**：HTTP 恒为 200，业务结果看响应体 `code`；`id` 是字符串、计数是数字、时间是 `yyyy-MM-dd HH:mm:ss`。

> 演示脚本可参照：注册 → 登录 → 搜索用户 → 关注 → 对方回关（互关）→ 打开私聊 → 发送消息 → 取消关注 → 确认**发送被拒但历史仍可读** → 重新关注 → **恢复发送**。

---

## 七、运行测试

```bash
# 先重置数据库（见下方说明）
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql

export DB_USERNAME=root DB_PASSWORD=你的密码
cd backend && mvn test
```

> ⚠️ **运行测试前必须先重置数据库。**
> 测试本身用 `@Transactional` 自动回滚，不会污染数据；
> 但**手工用 curl / 前端跑过的操作会真实提交**，之后测试断言种子数据（如"test001 有 2 条未读私信"）就会失败。
> 遇到大批量、看起来莫名其妙的断言失败时，先重跑一遍 `schema.sql` + `data.sql`。

当前 **291 个用例**（按 `@Test` 方法实测统计；早期文档写"212"为陈旧数字）：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `PostControllerTest` | 31 | 列表分页与倒序、关注流只含有被关注者、tab/size 参数校验、详情图片有序、发布（纯文字/纯图片/带图）、空内容 400、超长边界 1000 字、超 9 张图 400、**外链图片被拒**、删除权限（作者/他人 403/管理员）、级联清理 |
| `FollowControllerTest` | 26 | 关注/取消关注、重复关注 409、关注自己 400、关注禁用用户 423、关注不存在/已注销 404、计数增减、关注与粉丝列表、`followedByMe` 回关标识、互相关注、游客视角、**取消后可重新关注** |
| `ConversationControllerTest` | 22 | 会话列表倒序与未读数、**空会话不出现在列表**、幂等打开、**A↔B 对称性（规整后同一条记录）**、聊天记录正序、**游标分页不重不漏**、非参与者 403、标记已读只影响自己 |
| `CommentControllerTest` | 20 | 列表时间正序与分页、发表/空内容 400/超长边界 500 字、动态不存在 404、删除权限（作者/他人 403/管理员）、重复删除 404、评论通知与内容快照、自评不通知 |
| `AuthControllerTest` | 19 | 注册成功/用户名长度/字符规则/为空/密码长度/邮箱格式/重复用户名/重复邮箱/邮箱大小写归一化；登录成功/密码错误/用户不存在/账号禁用/账号注销/参数为空；退出登录 |
| `NotificationControllerTest` | 19 | 列表倒序、按类型/已读过滤、**数据按接收者隔离**、未读数分类与合计、单条已读幂等、**操作他人通知 403**、全部已读只影响自己 |
| `UserControllerTest` | 20 | 未登录拦截、伪造 Token、缺 Bearer 前缀、**禁用后旧 Token 立即失效**、注销账号 Token、不存在用户 Token、查询/修改资料/改头像（含**外链与近似前缀被拒**）/改密码全流程 |
| `MessageControllerTest` | 17 | 发送成功、自动建会话、复用已有会话、接收方未读 +1 / 发送方不增、预览截断 100 字、给自己发 400、接收者 404/423、内容边界 1000 字、未读数 |
| `UserSearchTest` | 16 | 昵称/用户名匹配、部分匹配、粉丝数倒序、**LIKE 通配符 `%` `_` `\` 转义**、游客与登录的 `followedByMe`、关键词长度与 size 校验 |
| `PostLikeControllerTest` | 12 | 点赞/重复点赞 409/取消/未点赞 404、**取消后可再次点赞**、反复切换计数一致、未登录 401、点赞通知生成与撤回、自赞不通知 |
| `AdminAccessControlTest` | 4 | 11 个后台接口的访问控制矩阵：未登录 401 / 非管理员 403 / 管理员 200 |
| `AdminUserControllerTest` | 14 | 用户列表与过滤、**关键词通配符转义**、禁用后旧 Token 立即失效、不能操作自己、操作日志落库 |
| `AdminContentControllerTest` | 13 | 动态/评论列表与过滤、管理员删除、级联清理、日志过滤与不含敏感信息 |
| `AdminReportControllerTest` | 18 | 举报列表与 `targetPreview`、**处置动作与目标类型校验**、驳回不能带处置、重复处理 409 |
| `AdminStatsControllerTest` | 9 | 统计口径（不含已注销）、今日新增、待处理举报随处理下降 |
| `ReportControllerTest` | 13 | 举报他人内容、不能举报自己、重复举报 409、目标不存在 404、类型非法 400 |
| `HealthControllerTest` | 4 | 健康检查：真实探测数据库 UP/DOWN、响应结构、免登录可访问 |
| `ResponseSerializationTest` | 6 | **id 是字符串、计数是数字、时间是格式化字符串**的全局约定 |
| `EntitySchemaConsistencyTest` | 2 | **实体标注与真实表结构一致性**（防线见下方说明） |
| `MiquApplicationTests` | 4 | 上下文装配、JWT 密钥长度、白名单不含 `/api/**`、分页上限 |
| `SeedPasswordTest` | 2 | `data.sql` 的 BCrypt 哈希确实对应 `123456` |

> **`EntitySchemaConsistencyTest` 值得单独说**。它源于一个真实踩过的坑：
> `Report` 曾经错误地继承了带 `@TableLogic` 的基类，而 `report` 表根本没有
> `deleted` 列——MyBatis-Plus 会给每条 SQL 追加 `WHERE deleted = 0`，
> 于是第一次查询这张表就抛 `Unknown column 'deleted'`。
>
> 这类错误**只要不查那张表就不会暴露**，实体写错半年都可能没症状，
> 直到某个新接口第一次用到它。现在这个测试会核对每个实体的
> `@TableLogic` 标注与表的 `deleted` 列是否一致，以及每个字段是否都有对应列。

### 接口自动化测试（pytest）

除 JUnit 外，`tests/` 下还有一套 **pytest 接口自动化**，通过真实 HTTP 打运行中的后端：

```bash
# 先启动后端，然后
pip install -r tests/requirements.txt
cd tests && python -m pytest          # 250 个用例
python -m pytest -m smoke             # 只跑冒烟（43 条）
python -m pytest -m read              # 只跑只读用例
```

两套测试是**互补**的，不是重复：

| | JUnit + MockMvc | pytest |
|---|---|---|
| 运行方式 | 进程内，不起 HTTP | 真实 HTTP + 真实数据库 |
| 事务 | `@Transactional` 自动回滚 | 真实提交，不回滚 |
| 擅长 | 规则密集的分支覆盖 | 端到端联调、序列化、契约验证 |

pytest 那套遵循两条纪律：**断言用差值不用绝对值**、**写数据只用当次新建的随机用户**。
因此它是**幂等**的——连跑两次结果一致，中途不需要重置数据库。

**250 个用例的分布：**

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `api/test_admin.py` | 53 | 后台 11 接口的访问控制矩阵、统计、用户/内容管理、举报处置、操作日志 |
| `api/test_post.py` | 31 | 发布与边界、列表分页、删除权限、点赞全流程 |
| `api/test_follow.py` | 19 | 关注/取关/重复/自关注/禁用/注销、关注与粉丝列表、回关标识 |
| `api/test_auth.py` | 19 | 注册校验、登录各失败分支、**禁用后旧 Token 立即失效** |
| `api/test_user.py` | 20 | 资料与密码修改、**不能越权改用户名/邮箱/角色**、白名单边界、**头像外链与近似前缀被拒** |
| `api/test_message.py` | 17 | 发私信建会话、未读数、标记已读、游标分页、非成员 403 |
| `api/test_notification.py` | 16 | 关注/点赞/评论通知的产生与撤回、自操作不通知、已读状态 |
| `api/test_search.py` | 15 | 昵称/用户名匹配、粉丝数倒序、**LIKE 通配符转义** |
| `api/test_message_mutual_follow.py` | 14 | **互关私聊规则（发送侧）** |
| `api/test_file.py` | 16 | **上传安全：魔数、大小、MIME、空/极小文件、可访问性**；**缺失静态资源返回真 404** |
| `api/test_comment.py` | 13 | 发表、长度与空内容、删除权限 |
| `api/test_conversation_mutual_follow.py` | 11 | **互关私聊规则（会话侧）**、非参与者隔离 |
| `api/test_concurrency.py` | 6 | **并发注册/关注/取关/点赞/建会话/发消息** |

**另有一层浏览器回归**（`tests/browser_regression.mjs`，真实 Chrome，15 项断言）：
前端状态一致性问题（切 tab / 换关键词 / 切通知类型后，标签与实际数据是否同源）。
这类缺陷 **pytest 与 JUnit 都测不到**——后端每次返回都是对的，错在前端没把请求发出去。
跑法：起后端 8081 与前端 5173 后 `NO_PROXY=localhost,127.0.0.1 node tests/browser_regression.mjs`。

详见 [`tests/README.md`](tests/README.md)（含已知覆盖缺口）。
测试体系全貌、用例规范、覆盖矩阵与**最近一次真实执行结果**见：

- [`docs/testing/MIQU_TEST_SYSTEM.md`](docs/testing/MIQU_TEST_SYSTEM.md) —— 体系总览与怎么跑
- [`docs/TESTING_GUIDELINES.md`](docs/TESTING_GUIDELINES.md) —— 写用例的规范与三条硬纪律
- [`docs/testing/TEST_COVERAGE_MATRIX.md`](docs/testing/TEST_COVERAGE_MATRIX.md) —— 接口/规则 → 用例对照
- [`docs/testing/TEST_EXECUTION_REPORT.md`](docs/testing/TEST_EXECUTION_REPORT.md) —— 本次执行数据
- [`test_cases/`](test_cases/README.md) —— YAML 用例（设计与管理文档，**不参与执行**）

> **`test_cases/` 与 pytest 的关系**：YAML 登记"冻结的业务规则长什么样"，
> pytest 是唯一可执行的事实来源，二者**不做 1:1 复制**（避免维护两份真相）。
> YAML 里的 `pytest` 字段是指针，可用
> `python test_cases/check_consistency.py` 校验所有指针都指向真实用例。

### 一个关于断言精度的约定

Bean Validation 对同一字段可能同时触发多条约束（空用户名会同时违反 `@NotBlank`、`@Size`、`@Pattern`），
而校验器**不保证触发顺序**。因此：

- 能构造出"只违反一条约束"的输入时 → 断言**精确文案**（如用户名仅长度不足）
- 无法避免多条约束同时命中时 → 只断言**错误码**，并在用例中注明原因

这样测试既严格，又不会因为校验器内部顺序变化而变成假失败。

---

## 八、配置项

`backend/src/main/resources/application.yml`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `${SERVER_PORT:8081}` | 见上方端口说明 |
| `spring.datasource.url` | `${DB_HOST}:${DB_PORT}/${DB_NAME:miqu}` | 可用环境变量拼装 |
| `spring.datasource.username` | `${DB_USERNAME:root}` | |
| `spring.datasource.password` | `${DB_PASSWORD:}` | **必须用环境变量注入** |
| `miqu.jwt.secret` | 开发用默认值 | ⚠️ **生产必须用 `JWT_SECRET` 覆盖**；HS256 要求 ≥32 字节，启动时校验 |
| `miqu.jwt.expire-seconds` | `604800`（7 天） | |
| `miqu.upload.base-dir` | `${UPLOAD_DIR:./uploads}` | 上传文件根目录 |
| `miqu.auth.white-list` | 注册/登录/健康检查 | 免登录白名单，策略是**失败关闭** |

**白名单是"失败关闭"的**：不在列表中的 `/api/**` 一律要求登录。
新增接口若忘记配置，默认是受保护的，而不是裸奔。格式 `METHODS:/ant/path`，METHODS 可省略。

---

## 九、实现进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 数据库脚本、项目骨架、统一响应与异常、Swagger | ✅ 完成 |
| P1 | 用户认证：注册 / 登录 / JWT / 用户信息 / 改密码 / 头像上传 | ✅ 完成 |
| P2 | 关注 / 动态 / 图片 / 点赞 / 评论（含通知生成与撤回） | ✅ 完成 |
| P3 | 私信（会话 + 消息）/ 通知查询与已读 / 用户搜索 | ✅ 完成 |
| P4 | 管理后台：数据统计 / 用户 / 动态 / 评论 / 举报 / 操作日志 | ✅ 完成 |
| P5 | 前端：Vue 3 + Vite + TS + Element Plus，含管理后台 | ✅ 完成 |
| P6 | pytest 接口自动化（250 用例）+ 互关私聊 / 文件安全 / 并发 / 前端状态专项 | ✅ 完成 |
| P7 | AI 测试用例生成（需求里的扩展目标） | ⬜ 待做 |

**全部功能已实现并验证**：后端 **50** 个接口、前端 20+ 页面、两套测试共 **541** 个用例
（JUnit 291 + pytest 250），另加前端浏览器回归 **15** 项断言，**当前 0 失败**。
最近一次执行结果见 [`docs/testing/TEST_EXECUTION_REPORT.md`](docs/testing/TEST_EXECUTION_REPORT.md)。
2026-09-14 全链路 Bug Hunt 的结论与证据链见
[`docs/testing/bug_report.md`](docs/testing/bug_report.md)（4 个 P2 缺陷已修，1 个证伪）。

> 私信采用 **REST + 轮询**，未引入 WebSocket（需求明确"不要因为追求技术而导致项目复杂化"）。
> 前端导航栏角标 15 秒轮询一次，聊天页打开时 3 秒轮询一次。

### 已知的取舍与缺口

诚实地记录，避免被误认为遗漏：

1. **Element Plus 全量引入**，产物 1.09MB（gzip 342KB）。
   改成 `unplugin-vue-components` 按需引入可压到 ~150KB，
   当前取舍是少两个构建插件、配置更直白。`vite.config.ts` 里有说明。
2. **「不能禁用管理员账号」这条分支未被测试覆盖**：
   种子数据里只有一个管理员，而管理员无法把自己作为禁用目标
   （"不能操作自己"会先命中），因此该分支没有输入能走到。
   详见 `tests/README.md` 的「已知覆盖缺口」。
3. **pytest 会产生持久数据**（新用户无法通过接口删除）。
   用例设计成幂等的，但库会缓慢增长；需要干净环境时重跑 `schema.sql` + `data.sql`。
4. **未做图片压缩与孤儿文件清理**：用户上传后放弃发帖会留下垃圾文件。
5. **未做 AI 测试用例生成**（P7）：需求里把它列为"后续可能加入"的扩展目标。

---

## 十、需求对照与设计偏离说明

以下几处**有意偏离**了需求原文或常见做法，在此显式记录，避免被误认为漏做：

### 1. 私信不写入通知表

需求原文将"收到私信"列为通知类型之一。本项目**刻意不实现**：
私信的新消息提示由会话未读数承载，若同时写入通知表，同一件事会在通知页和消息页出现两次。
`notification.type=4` 作为保留值存在，但一期不产生该类型的数据。

### 2. 取消点赞 / 取消关注返回 404 而非 200

`DELETE /api/posts/{id}/like` 对"本来就没点赞"返回 `404 NOT_LIKED`，
`DELETE /api/users/{id}/follow` 对"本来就没关注"返回 `404 NOT_FOLLOWED`。
语义明确、测试可断言。**连带要求**：前端点赞/关注按钮必须做请求中禁用态，否则连点两次会弹出错误提示。

### 3. 分页参数越界返回 400 而非静默重置

`?size=100000` 直接返回 400（`size ∈ [1, 50]`）。
静默改成 10 会让调用方以为生效了，也让自动化测试无法断言真实行为。
前端做"每页 100 条"这类选项时需把可选值限制在 50 以内。

### 4. 关系表不使用逻辑删除

`follow` / `post_like` / `post_image` 三张表**没有 `deleted` 字段**，取消关系即物理 `DELETE`。
若给它们加逻辑删除，取消点赞后唯一键 `uk_post_user` 仍被已软删的行占用，
用户将**永远无法再次点赞**——这会直接违反需求中"取消点赞后可以再次点赞"。

### 5. 用户表不支持注销，只用 status 禁用

唯一的 `username` / `email` 与逻辑删除天然冲突（注销后用户名仍被占用，新用户无法注册同名）。
因此一期只提供"禁用"（`status=0`），`deleted` 字段保留但无入口，
`deleted001` 种子账号仅用于验证逻辑删除的查询过滤行为。

### 6. 不建物理外键

用逻辑外键 + 索引 + Service 层校验代替。原因：逻辑删除与 `ON DELETE` 语义冲突，
建 FK 后批量造数据、清库、迁移都要按依赖顺序，测试脚本会很脆。

### 7. `logout` 无法在服务端强制失效

JWT 无状态，退出登录由客户端丢弃 Token 完成，已签发的 Token 在 7 天有效期内仍然可用。
若需要服务端强制失效，需引入 Redis 黑名单或改用短期 access token + refresh token。
**注意**：账号被**禁用**是立即生效的——认证过滤器每请求查库校验 `status`，这一点与 logout 不同。

---

## 十一、安全相关的实现要点

这些点在实现中已经落实，修改代码时请勿破坏：

1. **密码**：BCrypt 哈希存储；`User` 实体的 `password` 字段有 `@JsonIgnore` + `@ToString.Exclude`；
   `UserVO` 与实体严格分离，响应中永不出现密码。日志只记录 `userId` / `username`，绝不记录密码或 Token。
2. **禁用立即生效**：`JwtAuthenticationFilter` 解析 Token 后**必须查库**校验 `status` / `deleted`，
   否则管理员禁用用户后，对方手里的旧 Token 在有效期内依然畅通。
3. **防用户名枚举**：登录时"用户不存在"与"密码错误"返回**完全相同**的 401 响应。
4. **防越权赋值**：所有写操作使用专用 DTO 接收，不允许用实体接参
   （否则前端传 `role=2` 就能把自己变成管理员）。
5. **上传安全**：按**文件头魔数**判断图片类型（改后缀名的脚本会被拒绝），
   存储时用 UUID 重命名（杜绝路径穿越），并有目录逃逸检查。
6. **防全表更新**：MyBatis-Plus 的 `BlockAttackInnerInterceptor` 已开启，
   不带 `WHERE` 的 `UPDATE` / `DELETE` 会直接抛异常。
7. **唯一键冲突翻译**：`DuplicateKeyException` 按索引名映射为业务错误码（如 `uk_post_user` → 409 "已点赞"），
   这是**并发下防重复的唯一正确兜底**。改索引名时需同步 `GlobalExceptionHandler`。
8. **白名单失败关闭**：见第八节。

---

## 十二、开发约定

- Controller 只做参数绑定与调用，**不写业务分支**
- Service 承载业务规则与事务边界，抛 `BizException(ErrorCode)`
- Mapper 只做数据访问，计数类字段一律用**原子 UPDATE**（`x = x + 1`），
  禁止"查出来 +1 再写回"（并发会丢失更新）
- 禁止直接返回 Entity，一律经 `converter/` 转 VO
- `createTime` / `updateTime` 由 `MyMetaObjectHandler` 自动填充，禁止手写
- 长度、数量上限集中在 `BizConstants`，既是校验注解的取值来源，也是测试边界值的来源
