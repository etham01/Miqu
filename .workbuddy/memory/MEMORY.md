# Miqu 项目长期记忆

> 跨会话复用的项目事实与**本机环境坑**。日常流水账见同目录 `YYYY-MM-DD.md`。

## 项目真实规模（2026-09-16 实测，简历与文档用这套数字）

```
11 张表 / 16 个 Controller / 50 个 REST 接口 / 20+ 前端页面
自动化用例 541 = JUnit 291 + pytest 250，另加浏览器回归 15 项断言
当前 0 失败
```

- 旧文档的「38 个接口 / 518 用例 / pytest 229 / 201」都是陈旧数字，已全部更正。
- pytest 文件分布见 `docs/api/README.md` §11。
- 接口清单权威来源是 `docs/api/README.md`（不是 `docs/design.md` §4，后者是设计态、不全）。
- **Bug Hunt 结论见 `docs/testing/bug_report.md`**：7 个已修（BUG-002~007），
  BUG-001 证伪（取消关注后关注流行为本来就是对的），BUG-008 待决策。
  头像问题的完整诊断另见 `docs/testing/avatar-issue-diagnosis.md`。
- 文档数字批量同步脚本：`.workbuddy/tools/sync_docs_numbers.py`（逐条报命中，可审计）。

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

### 11. bash 里批量 curl 会假报"全挂"

把 `curl` 放进 `while read` 循环、或用 `-o /dev/null` 时，
会批量返回 `000` / 0 字节，报 `curl: (23) client returned ERROR on write`，
**看起来像远端全挂，其实是沙箱写输出的假象**（2026-09-16 差点据此误判"图床全挂了"）。

**正确做法**：单个 curl 写真实文件；或改用 **Node 的 `fetch` 在单进程里顺序验证**
（已沉淀为 `.workbuddy/tools/check_seed_avatars.mjs`）。
**任何"批量失败/单个成功"的现象，先怀疑工具，再怀疑被测对象。**

### 12. bash 工具的输出管道会**截断/损坏长字符串**

2026-09-17 实测：一个 180 字符的 JWT，`echo`/`print` 出来只剩 22 字符
（`eyJhbGciOiJIUzI1NiJ9...` → `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyNyIsInVzZXJuYW1lIjoicWFkZWwxMTM2NzliNTg4Iiwicm9sZSI6MSwiaXNzIjoibWlxdSIsImlhdCI6MTc4OTYyMTQwOSwiZXhwIjoxNzkwMjI2MjA5fQ.mo4DCIH3r1bPrHniARij40ib6-st48cL1faxQrwLBVg`）。
差点据此误判"JWT 实现不对"。

**正确做法**：长字符串（token / 大 JSON / 长路径）**写文件再用 Read 工具读**，
不要在 bash 里 echo/print 后靠肉眼看。这是 #11 那条坑的同族问题。

### 13. 写中文 YAML 的四种"必炸"写法（都踩过）

| 写法 | 后果 |
|---|---|
| `key: "中文里带"裸引号"的值"` | 解析失败。**用 `「」`** |
| `key: 值里有 ": "（冒号+空格）` 且未加引号 | 解析失败（`mapping values are not allowed here`）。**加引号** |
| 流式映射 `{a: 1, or: 2}` | `or` 作为 flow key 会让 PyYAML 6.0.3 直接炸（`and`/`not` 不会；`on` 会**静默变布尔 True**） |
| 流式映射 `{...}` 里放 `>-` 块标量 | 解析失败。流式集合里不能有块标量，改块风格 |
| 序列项 `- key: value` 被包成 `- "key: value"` | 序列项退化成字符串，下一行子键无处安放 |

工具：`.workbuddy/tools/fix_yaml_quotes.py`（**带安全闸**，只在文件本来不解析且改完好转时才落地）、
`.workbuddy/tools/repair_yaml_quotes.py`（以"能否解析 + 错误位置是否推进"为裁判回滚误伤）。

**纪律**：写完任何 YAML 立刻 `python -c "import yaml;yaml.safe_load(open(p,encoding='utf-8'))"`。
不要写"自动批量改写 YAML"的脚本却不给它一个裁判 —— 那一次性弄坏过 8 个文件。

## `test_knowledge/` AI 测试知识库（2026-09-17 建成）

**定位**：给另一个 AI 测试系统做 Grounding 的**结构化事实底座**，不是给人看的文档。
`648 KB / 24 个文件`（16 YAML + 2 MD + 1 py）。

```bash
python test_knowledge/validate.py        # 28 项检查，退出码 0 = 自洽
python test_cases/check_consistency.py   # 项目原有校验，退出码 0
```

两个校验器职责不重叠：前者查**知识库自身**（引用有效性 / 计数 / ID 唯一 / 可解析），
后者查 `test_cases/` 的用例指针。

**最重要的 4 个文件**：
- `rules/validation_order.yaml` —— 跨层校验优先级（**别处没有的知识**）
- `rules/business_rules.yaml` —— 规则全集（正文 86 条 / 提及 92 个 ID）
- `ai_grounding.yaml` —— **38 条"禁止假设"**，防 LLM 凭社交应用常识补全
- `issues.yaml` —— 6 CONFLICT / 4 INFERRED / 4 UNKNOWN / 9 MISSING，**不隐藏问题**

### 实测得到的三条硬事实（2026-09-17，别再推理）

1. 🔴 **认证层（拦截器 preHandle）压过参数层（Bean Validation）**
   禁用账号 + 非法请求体 → **423**，不是 400。
   机制：`preHandle` 在参数解析之前执行。
   → `requireActiveUser(currentUserId)` 的 401/404/423 分支在 HTTP 层**不可达**；
   但 `requireActiveUser(targetUserId)`（路径参数传入）的 **404 可达**。
2. 🔴 **通知撤回是逻辑删除**（`Notification extends BaseEntity` → `UPDATE deleted=1`）；
   同一次取消关注里的 **`follow` 行是物理删除**。写 SQL 断言前必须分清。
3. 🔴 **`PUT /api/admin/reports/{id}/handle` 对不存在的举报返回 404**（`NOT_FOUND` 自带的码），
   文案被覆盖为「举报不存在」。原先知识库记的 400 是错的。

### 知识库里已修正的历史错误（追溯用）

- 3 个 YAML 根本无法解析（`or` flow key / 裸引号 / 缩进错位）
- `endpoints.yaml` 悬空引用 `SRCH-004..008` → `RPT-004..008`
- `endpoints.yaml` 记举报不存在返回 400 → 实为 404
- `endpoints.yaml` 称通知撤回复"物理删除" → 实为逻辑删除
- `business_rules.yaml` 的 `meta.total_rules_in_this_file: 78` → 实为正文 86

## 前端约定：loading 守卫必须区分「去重」与「新查询」

`if (loading.value) return` 这种写法在**改筛选条件**的场景是错的：
筛选条件（tab / keyword / type）先变、取数请求被丢 → **标签与数据不一致**。
2026-09-14 在 `HomeView` / `SearchView` / `NotificationView` 三处复现（见 `docs/testing/bug_report.md`
BUG-002/003/004）。正确写法：请求序号，只丢**过期响应**，不丢**用户的新查询**。
`CommentList` 目前同样是该写法，但因没有切换筛选条件的入口而**不可达**——将来加筛选必踩。

## 头像统一走 `UserAvatar.vue`，两处已知弱点（2026-09-16 诊断）

- **全项目 15 处头像渲染共用 `components/UserAvatar.vue`** → 改一处即全站生效，但坏一处也全站坏。
- ✅ **已修（BUG-006）**：原先只有 `v-if="src"`、没有 `@error` → URL 非空但取不到图时**永久破图**。
  现加了 `failed` 状态 + `@error` 兜底 + `watch(props.src)` 重试。**改头像相关的渲染问题先看这里。**
- ✅ **已修（BUG-007）**：`/uploads/**` 缺失文件原先返回 **HTTP 200 + `application/json`**，
  `<img>` 解码失败。现在 `GlobalExceptionHandler.handleNoResource` 按 URI 分流，
  静态资源返真 404。**这是「HTTP 恒 200」约定的唯一例外，`/api/**` 未动**
  （对照用例 `test_unknown_api_path_keeps_http_200` 钉住）。
- ⏸ **未修（BUG-008）**：`PUT /users/me/avatar` 不校验文件是否存在。
  修它会打破 2 条既有用例（`test_update_avatar` / `updateAvatar_success` 都传了不存在的路径却断言 200）
  → 按纪律停下等拍板。**不要擅自改这两条用例。**
- 完整诊断与修复记录见 `docs/testing/avatar-issue-diagnosis.md`。

## 用例 YAML 之前是"假通过"的（2026-09-16 发现并修）

`test_cases/*.yaml` 里 4 处 `steps: [并发 POST /api/users/{id}/follow]` 是**非法 YAML**
（`{` 不能出现在流式序列里）。之前没暴露是因为机器上**没装 PyYAML**，
`check_consistency.py` 走了正则兜底分支。装上 PyYAML 后真解析直接崩。
**已修**：给 4 行加引号，并让校验器把 `yaml.YAMLError` 当问题报出来（退出码 1）而不是抛栈。
→ 教训：**校验脚本有"降级分支"时，必须确认它到底走了哪条分支**，否则绿灯是假的。

## 未验证就不要下结论：`vite preview` 是代理的

Vite 的 **`preview.proxy` 默认继承 `server.proxy`**，实测 4173 上 `/api` 与 `/uploads` 都通。
不要因为 `vite.config.ts` 里只写了 `server.proxy` 就断定 preview 不代理。

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
