# Miqu 项目长期记忆

> 跨会话复用的项目事实与**本机环境坑**。日常流水账见同目录 `YYYY-MM-DD.md`。

## 项目真实规模（2026-09-14 实测，简历与文档用这套数字）

```
11 张表 / 16 个 Controller / 50 个 REST 接口 / 20+ 前端页面
自动化用例 539 = JUnit 291 + pytest 248，另加浏览器回归 12 项断言
当前 0 失败
```

- 旧文档的「38 个接口 / 518 用例 / pytest 229 / 201」都是陈旧数字，已全部更正。
- pytest 文件分布见 `docs/api/README.md` §11。
- 接口清单权威来源是 `docs/api/README.md`（不是 `docs/design.md` §4，后者是设计态、不全）。
- **Bug Hunt 结论见 `docs/testing/bug_report.md`**：4 个 P2 已修（BUG-002~005），
  BUG-001 证伪（取消关注后关注流行为本来就是对的）。

## 本机环境坑（每次跑项目都会遇到，务必先看）

### 1. Bash 工具的 PATH 是坏的
`ls`/`head`/`dirname` 等报 command not found。**每条 bash 命令开头都要加**：

```bash
export PATH="/c/Users/张志鹏/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:/c/Users/张志鹏/.workbuddy/binaries/PortableGit/versions/1.2.0/bin:$PATH"
```

### 2. `mvn` 命令本身不可用
直接跑 `mvn` 报 `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`。
已封装可用包装脚本：**`bash .workbuddy/tools/mvn.sh test`**（直接以 java 调 maven launcher）。
需要 `JAVA_HOME=D:\soft\java\Java\jdk-21`（系统 PATH 里的 java 是 25，别用）。

### 3. 沙箱会注入 `SERVER__PORT=16605`
Spring Boot 宽松绑定会把它当 `server.port`，导致后端抢不到端口/起不来。
**启动后端必须显式指定端口**：

```bash
env -u SERVER__PORT -u SERVER_PORT "$JAVA_HOME/bin/java" \
    -jar backend/target/miqu-backend-1.0.0.jar --server.port=8081
```

### 4. 长驻进程必须用 `run_in_background=true` 直接跑
`nohup ... &` 会在该次 bash 调用结束时被沙箱杀掉。把 java/node 进程本身作为后台任务启动。

### 5. HTTP 走沙箱代理，本机请求要绕开
环境里有 `HTTP_PROXY=127.0.0.1:16668`。curl 加 `--noproxy '*'`；
pytest 运行前 `export NO_PROXY=localhost,127.0.0.1 no_proxy=localhost,127.0.0.1`。

### 6. 用哪个 Python
- 带 pytest/requests 的是**系统 Python 3.12**：`/d/tools/Python/python.exe`
- 给 Windows 版 python 传路径要用 `D:/...` 形式（不认 `/d/...`）
- 托管 python（3.13）**没有** pytest

### 7. 端口
后端 **8081**（8080 被 Windows 服务 ApplicationWebServer.exe 占用）；前端 dev **5173**；
MySQL **3306**。

### 8. Vite 只绑 IPv6
`vite` 起在 5173 后 **只监听 `::1`**：`curl http://127.0.0.1:5173` 返回 `000`，
必须用 **`http://localhost:5173`**（playwright 的 `MIQU_FRONT_URL` 同理）。

### 9. 浏览器自动化
playwright 装在本机 `D:/tools/playwright/`，用
`createRequire('D:/tools/playwright/')('playwright')` 加载，`chromium.launch({channel:'chrome'})`
走本机已装 Chrome，**不需要 npx playwright install**。

## 测试纪律（改测试前必读）

1. **pytest 红了先怀疑环境**：① 种子数据被手工改过？→ 重置 DB；② 后端没重启？→ 重新打包。
2. **重置数据库**（会清空 `miqu` 库）：
   `mysql -uroot -proot < database/schema.sql && mysql -uroot -proot < database/data.sql`
3. **JUnit 依赖种子绝对值**，跑 `mvn test` 前必须先重置；pytest 用差值断言，容忍脏数据。
4. **禁止**用删测试 / skip / 放宽断言 / 改种子来让测试变绿。
5. pytest 套件**幂等**（连跑两次一致），但会持续积累测试用户——这是预期行为。

## 冻结业务规则

### 互关私聊（2026-09-11 冻结）
- 双方互相关注才能发私信 / 打开会话；非互关（含单向）→ 403 `NOT_MUTUAL_FOLLOW`
- **限制的是"发起"，不是"查看"**：历史消息读取与标记已读不受限制
- 被拒的发送**不产生会话/未读/消息**（互关校验在写库之前）
- 校验优先级（实测）：**DTO Bean Validation(400) → 不能给自己(400) → 接收者 404/423 → 互关(403)**

### 关系表约定
`follow` / `post_like` / `post_image` **物理删除、无 deleted 列** —— 否则唯一键会被软删记录占用，
导致"取消后无法重新关注/点赞"。`user`/`post`/`comment`/`message`/`notification` 才用逻辑删除。

## 其它项目约定

- **HTTP 恒 200**：`GlobalExceptionHandler` 无 `@ResponseStatus`，结果看 body 的 `code`。
  唯一例外：`GET /api/health` 在 DB 挂时 `code=500`。
- 序列化：**id 是字符串**（防精度丢失），**计数是数字**，**时间是格式化字符串**。
- 白名单"失败关闭"：不登记在 `miqu.auth.white-list` 的 `/api/**` 一律要登录。
- 通知类型是**数字**：1=关注 2=点赞 3=评论（4=私信为保留值，一期不产生）。

### 10. `mvn package` 会被运行中的后端锁住 jar

报错：`Unable to rename '...miqu-backend-1.0.0.jar' to '...jar.original'`。
**编译是成功的，只有 repackage 失败**——因为 java 进程还开着那个 jar。
先停 8081 的 java 进程再打包。

顺带记住这个正确顺序（否则会反复踩）：
**重置库 → `mvn test` → 再重置库 → 启后端 → pytest → 浏览器回归**。
`mvn test` 期间后端必须停着（否则又锁 jar），且它依赖种子绝对值，前面必须先重置。

## 前端约定：loading 守卫必须区分「去重」与「新查询」

`if (loading.value) return` 这种写法在**改筛选条件**的场景是错的：
筛选条件（tab / keyword / type）先变、取数请求被丢 → **标签与数据不一致**。
2026-09-14 在 `HomeView` / `SearchView` / `NotificationView` 三处复现（见 `docs/testing/bug_report.md`
BUG-002/003/004）。正确写法：请求序号，只丢**过期响应**，不丢**用户的新查询**。
`CommentList` 目前同样是该写法，但因没有切换筛选条件的入口而**不可达**——将来加筛选必踩。

## 前端回归测试在哪

- `tests/browser_regression.mjs` —— 11 项断言的真实 Chrome 回归（BUG-001 固定回归 + 缺陷族）。
  **不进 pytest 收集**（`.mjs` + `pytest.ini` 的 `testpaths=api`），所以它红不会污染 pytest 的绿灯。
  跑法：先起后端 8081 与前端 5173，再 `NO_PROXY=localhost,127.0.0.1 node tests/browser_regression.mjs`。
- `tests/browser_check.mjs` —— 页面走查（控制台报错 + 未捕获异常）。
- 只读探针在 `.workbuddy/tools/`（api_boundary_probe / race_family_probe / bug001_probe 等）。

## 已登记但未修的缺口（不要假装覆盖）

- 「不能禁用管理员账号」分支 HTTP 层不可达（只有一个管理员，且"不能操作自己"先命中）
  → 对应 YAML 用例 `SEC-004`
- `uk_users` 冲突被映射为通用 `CONFLICT(409)`，日志文案称"未映射"，有误导性
- 数十线程同瞬间首建会话未压测（只验证到 8 线程）→ 对应 YAML 用例 `CONC-007`
- 前端无单元/组件测试（有意取舍）
- 种子头像依赖外部图床 `i.pravatar.cc`（21 个），离线时显示破图
