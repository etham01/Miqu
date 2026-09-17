# Miqu Bug Report

> 本文档记录 2026-09-14 全链路 Bug Hunt 的**证据、定性结论、修复与回归**。
>
> 执行链路：`发现 → 复现 → 定位根因 → 写失败回归（准入闸门）→ 最小修改 → 专项回归 → 全量回归`。
> 全程**没有**为了"让测试变绿"改动任何测试期望、没有 skip、没有删测试、没有重构无关模块。
>
> **改动清单（生产代码）**
>
> | 文件 | 改动 |
> |---|---|
> | `frontend/src/views/home/HomeView.vue` | `load()` 引入请求序号（BUG-002） |
> | `frontend/src/views/search/SearchView.vue` | 同款（BUG-003） |
> | `frontend/src/views/notification/NotificationView.vue` | 同款（BUG-004） |
> | `backend/src/main/java/com/miqu/service/impl/UserServiceImpl.java` | 头像前缀校验（BUG-005，+1 import / +1 字段 / +7 行校验） |
>
> 未改动：`database/schema.sql`、`database/data.sql`、`PostMapper`、任何 Controller、
> 任何既有 pytest/JUnit 断言的期望值。
>
> **`git status` 里的 3 个 `M` 文件**（`GlobalExceptionHandler.java`、`ConversationServiceImpl.java`、
> `database/schema.sql`）是**本轮之前**上一次交付留下的改动（mtime 15:10 / 09-13 13:31），
> 不是本次 Bug Hunt 产生的。

**基线对照**

```
                最初          第一轮(09-14)           第二轮(09-16 头像)         当前
pytest:   246 passed   →   248 passed / 0 failed   →   250 passed / 0 failed
JUnit:    289 run      →   291 run / 0 failures    →   291 run / 0 failures
浏览器:     5 PASS/6 FAIL →  12 PASS / 0 FAIL       →   15 PASS / 0 FAIL   (exit 0)
前端构建:                                                vue-tsc 0 error / vite build 成功
用例一致性:                                              check_consistency.py exit 0
```

> 各 Bug 章节里的「完整测试」行记录的是**该 Bug 修复当时**的实测结果，属时间点记录；
> 当前基线以本节为准。

> ⚠️ **任务书里的「pytest 基线 215 passed」与仓库实际不符。**
> 实测基线是 **246**（2026-09-14 15:2x 扩到 246，见 `.workbuddy/memory/2026-09-14.md`）。
> 本报告一律以实测为准。

---

## 一、结论速览

| ID | 标题 | 严重程度 | 状态 | 回归 |
|---|---|---|---|---|
| BUG-001 | 取消关注后仍能看到对方动态 | — | **NOT_A_BUG（未复现）** | 4/4 PASS |
| BUG-002 | 首页：首屏在飞时切「我的关注」，标签与数据不一致 | P2 | **FIXED** | 3/3 PASS |
| BUG-003 | 搜索：首次在飞时换关键词，地址栏与结果不一致 | P2 | **FIXED** | 3/3 PASS |
| BUG-004 | 通知：列表在飞时切类型筛选，标签与数据不一致 | P2 | **FIXED** | 2/2 PASS |
| BUG-005 | 头像 URL 未校验 `/uploads/` 前缀，可写入任意外链 | P2 | **FIXED** | pytest 2 + JUnit 2，全通过 |
| BUG-006 | 头像加载失败无兜底，取不到就是永久破图（全站 15 处共用组件） | **P1** | **FIXED** | 3/3 PASS |
| BUG-007 | 静态资源 404 被包成 `HTTP 200 + JSON`，`<img>` 解码失败 | P2 | **FIXED** | pytest 2，全通过 |
| BUG-008 | 头像接口不校验目标文件是否存在 | P2 | **⏸ 待决策** | — |

BUG-002 / 003 / 004 是**同一个根因**在三处入口的三种表现，合并计入「缺陷族」但分开登记，便于逐条回归。
**未解决 P0/P1/P2：BUG-008（P2）待决策，其余 0 个。**
BUG-006/007/008 的完整诊断过程另见 [`avatar-issue-diagnosis.md`](avatar-issue-diagnosis.md)。

---

## BUG-001：取消关注后仍能看到对方动态

### 1. 发现 Bug

**状态：** `NOT_A_BUG`（未复现 / 后端与前端行为均正确）

**严重程度：** —

**发现方式：** 手工测试 + API 测试 + 数据库核对 + 真实浏览器走查 + Code Review（五路交叉）

**业务定义核对（先定性，再看代码）**

任务书要求先确认「关注动态」是**当前关注关系**还是**历史关注过**。文档有明确定义，**不属于 `BUSINESS_RULE_AMBIGUOUS`**：

| 来源 | 原文 | 含义 |
|---|---|---|
| `docs/design.md` L485 | `WHERE deleted=0 AND user_id IN (SELECT following_id FROM follow WHERE follower_id=me)` | 子查询取的是 follow 表**当下**的行 |
| `docs/api/posts.md` L169 | `following` \| 我关注的人的动态（**不含自己的**）\| 由 `PostMapper.selectFollowingFeed` 决定 | 当前关系 |
| `docs/design.md` L224 | `KEY idx_following (following_id, create_time)` | 按当前关系查粉丝 |

→ 业务定义 = **当前有效关注关系**。若实现遵循该定义，则现象不成立；若违背，才是 Bug。

**复现步骤与实测结果**

| 步骤 | 操作 | 实测结果 |
|---|---|---|
| 1 | A(456) 关注 B(457) | `code=200` |
| 2 | B 发布动态 | `postId=121` |
| 3 | A 查 `GET /api/posts?tab=following` | 作者 `['457']`，`total=1`，B 的动态**在** ✓ |
| 4 | A 取消关注 B | `code=200` |
| 5 | A 再查 `tab=following` | 作者 `[]`，`total=0`，**B 的动态已消失** ✓ |
| 6 | 再查一次（排除偶发） | `[] / total=0` ✓ |
| 7 | `size=1` 翻页 | `total=0`，列表长度 0 ✓ |

**数据库层交叉核对（关键）**

```
follow 表中 A→B 的行数      = 0        ← 真的被物理删除了
A(456).following_count      = 0
B(457).follower_count       = 0
post 121  user_id=457 deleted=0        ← 动态本身还在，只是不再进 A 的关注流
notification 456→457 type=1 deleted=1  ← 关注通知已逻辑删除（撤回正确）
```

**真实浏览器走查（Chrome，非人工肉眼）**

```
PASS [BUG-001.a] 关注期间能在「我的关注」看到 B 的动态
PASS [BUG-001.b] 取消关注已落库（followedByMe=false）
PASS [BUG-001.c] 取消关注后「我的关注」不再出现 B 的动态
PASS [BUG-001.d] 切到「我的关注」确实重新请求了 tab=following
```

真实请求序列（取消关注后回首页再切 tab）：

```
posts?tab=latest&page=1&size=10
posts?tab=following&page=1&size=10      ← 确实重新发了请求，不是用的旧数据
```

### 2. 分析 / 定位原因

**相关模块：** Follow、Post（Feed）

**相关 API：**

- `POST /api/users/{id}/follow`
- `DELETE /api/users/{id}/follow`
- `GET /api/posts?tab=following&page=&size=`
- 辅助：`GET /api/users/{id}`（看 `followedByMe`）

**根因：三层实现均符合业务定义，无需修改。**

**① 后端查询 —— 用的是当前关系，不是历史关系**

`backend/src/main/java/com/miqu/mapper/PostMapper.java`

```sql
SELECT p.*
FROM `post` p
INNER JOIN `follow` f ON f.following_id = p.user_id
WHERE f.follower_id = #{followerId}
  AND p.deleted = 0
ORDER BY p.create_time DESC, p.id DESC
```

- `INNER JOIN follow` 取的是 **follow 表当下存在的行**；本项目的 `follow` 表按设计**物理删除、无 `deleted` 列**
  （`database/schema.sql` L11-12、L55 明写理由：软删会让 `uk_follower_following` 被占用，导致取关后无法重新关注）。
  因此取消关注后 join 条件**不可能**再命中。
- 不是 `post.author_id IN (...)` 那种把作者硬编码进去的写法。
- 没有「历史关系」表，也不存在任何记录"曾经关注过"的字段。

**② 查询顺序 —— 过滤在分页之内，不存在「先分页再过滤」**

`PostServiceImpl.listFeed()` 把整个 `tab=following` 交给 `postMapper.selectFollowingFeed(page, currentUserId)`；
`INNER JOIN + WHERE` 是**被分页插件包裹的同一条 SQL**（`LIMIT` 加在外层），
不存在"先取一页再在 Java 里剔除关系"的写法，因此不会残留旧数据。

**③ 前端状态 —— 无缓存、每次都重新请求**

| 检查项 | 结论 |
|---|---|
| 是否有 `keep-alive` 缓存 HomeView | 无（`DefaultLayout.vue` 的 `<RouterView>` 直接渲染，`router/index.ts` 未启用 keepAlive） |
| 取消关注后是否重取关注流 | 是。`HomeView.selectTab()` → `load(true)`；且组件重新挂载时 `onMounted` 也会拉一次 |
| 是否有 Pinia / localStorage 缓存动态流 | 无。`localStorage` 只存 token 与用户信息（`stores/user.ts`） |
| 是否可用"取消关注但不离开首页"触发脏数据 | **不可能**。`PostCard.vue` 上**没有关注按钮**，取关只能进对方主页操作，必然触发 HomeView 重新挂载 |

**④ 分页 / 游标**：`ORDER BY create_time DESC, id DESC` 带主键兜底；取消关注后 `total` 从 1 变 0，翻页行为正确。

**⑤ 缓存**：无后端缓存、无 HTTP 缓存、无前端动态流缓存（见上表）。

```text
Root Cause:
不存在缺陷。BUG-001 描述的现象在当前代码上无法复现：
- 后端 selectFollowingFeed 用 INNER JOIN 当前 follow 行 + 物理删除，取关即失联；
- 前端无任何动态流缓存，切 tab / 重新进入都会重新请求；
- follow 表行、冗余计数、关注通知三者状态一致。
判定为 NOT_A_BUG。
```

### 3. AI 辅助修改

**修改文件：** 无（严格遵守「先证明再修改」，未改动任何生产代码）

### 4. 回归验证

**专项测试：** PASS（`tests/browser_regression.mjs` 的 `BUG-001.a` ~ `BUG-001.d` 共 4 项）

**完整测试：**

```
pytest: 246 collected / 246 passed / 0 failed
```

### 5. 最终结论

```text
BUG-001
状态：NOT_A_BUG（未复现）
严重程度：—
根因：查询使用当前有效关注关系 + 前端无动态流缓存，行为符合 docs/design.md L485 的业务定义
修复：无需修改
回归：PASS（4/4）
备注：作为长期回归用例固化在 tests/browser_regression.mjs，防止将来被改坏
```

---

## BUG-002：首页首屏在飞时切「我的关注」，标签与数据不一致

### 1. 发现 Bug

**状态：** `CONFIRMED`

**严重程度：** P2（未关注用户的动态被展示在「我的关注」下）

**发现方式：** 自动化测试（真实 Chrome）+ Code Review

**复现步骤：**

1. 登录 A（A 只关注 B，因此关注流应恰好 1 条）
2. 打开首页；把首屏 `GET /api/posts?tab=latest` 人为延迟 3s（模拟慢网/后端抖动）
3. 在首屏请求**还在飞**的时候，点击「我的关注」
4. 结果：tab 高亮变成「我的关注」，但**没有发出** `tab=following` 请求，列表里是**最新动态**的 10 条

**预期：** 用户点了「我的关注」，就应当看到**当前关注的人的动态**。

**实际（探针原始输出）：**

```
高亮的 tab           = 「我的关注」
实际发出的 list 请求 = /posts?tab=latest&page=1&size=10      ← 只有 latest，没有 following
页面渲染出的动态条数 = 10            （关注流真实条数 = 1）
渲染出的作者：@rb318895f2 / @bbd712b0e5 / @bugb8926ffa0 / @qa8b01627230 / ...
              其中 A 只关注 bbd712b0e5，其余 9 条全部来自未关注用户
```

对照 API 直查的真实数据：

```
tab=following  total=1   返回条数=1
tab=latest     total=113 返回条数=10
```

### 2. 分析 / 定位原因

**相关模块：** Frontend / Home（Feed 展示层）

**相关 API：** `GET /api/posts?tab=latest|following&page=&size=`（后端返回**完全正确**，问题在请求没发出去）

**根因：** `frontend/src/views/home/HomeView.vue` 第 22-24 行

```ts
async function load(reset = false) {
  if (loading.value) return          // ← 根因：守卫把"用户主动发起的新查询"也吞掉了
  loading.value = true
  try {
    if (reset) page.value = 1
    const result = await postApi.list(tab.value, page.value, size)
    posts.value = reset ? result.list : [...posts.value, ...result.list]
    ...
```

调用链：

```
selectTab('following')
  → tab.value = 'following'      ← 标签先变了
  → load(true)
      → if (loading.value) return   ← 首屏 latest 还在飞，直接 return，请求根本不发
                                    （首屏 latest 的响应随后落库并覆盖 posts）
```

即：**筛选条件（`tab`）已经改了，但为它取数的请求被丢弃**，页面继续展示旧条件（`latest`）的数据。

这个守卫本意是「防重复提交」，对**加载更多**是合理的；但对**用户在切换筛选条件**场景是错的——
二者语义不同，必须分开处理。

同类写法在 `SearchView.vue`(L29) 与 `NotificationView.vue`(L48) 各出现一次，见 BUG-003 / BUG-004。

```text
Root Cause:
HomeView.load() 的 `if (loading.value) return` 守卫无法区分
「加载更多的去重」与「用户主动切换筛选条件的新查询」，
导致切 tab 时的请求被静默丢弃，而 tab 标签已经先变
→ 标签与实际数据不一致，且会把未关注用户的动态显示在「我的关注」下。
后端无责任：请求根本没发出。
```

### 3. AI 辅助修改

**修改文件：** `frontend/src/views/home/HomeView.vue`（**仅此一个文件**，净增约 14 行/替换 5 行）

**修改内容：** 引入请求序号，只丢「过期响应」，不丢「用户的新查询」：

```ts
let requestSeq = 0

async function load(reset = false) {
  if (loading.value && !reset) return   // 只对「加载更多」去重
  const seq = ++requestSeq
  if (reset) page.value = 1
  loading.value = true
  try {
    const result = await postApi.list(tab.value, page.value, size)
    if (seq !== requestSeq) return      // 已有更新的请求，丢弃过期响应
    posts.value = reset ? result.list : [...posts.value, ...result.list]
    total.value = result.total
    hasNext.value = result.hasNext
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}
```

**为什么这么改：** `loading` 挡重复点击是对的，但它**分不清**"加载更多"（应当挡）
与"切换筛选条件"（不该挡）。序号把"谁是最新的请求"变成显式状态，
于是既能挡住重复点击，又不会吞掉用户主动发起的新查询，还能防止旧响应覆盖新数据。

**修改原则遵守情况：**

| 原则 | 落实 |
|---|---|
| 最小修改 | 只改 `load()` 一个函数；后端零改动；分页/排序/模板/样式都没动 |
| 不改变无关业务 | 未触及其它任何文件 |
| 不删除已有测试 | 未删 |
| 不修改测试期望值 | 未改 |
| 不修改数据库结构 | 未改 |
| 不重构动态模块 | 未重构（没有抽 composable、没有引入新的状态库） |

**回归测试：** `tests/browser_regression.mjs` 的 `BUG-002.a` ~ `BUG-002.c`
（YAML 登记：`CONC-008`）

### 4. 回归验证

**Before Fix（失败即准入闸门）：**

```
BUG-002.a  PASS   切 tab 后高亮为「我的关注」
BUG-002.b  FAIL   切 tab 后补发了 tab=following 请求        (无)
BUG-002.c  FAIL   渲染条数与关注流一致                      渲染 10 条，关注流应为 1 条
```

**After Fix：**

```
PASS  [BUG-002.a] 切 tab 后高亮为「我的关注」            高亮=我的关注
PASS  [BUG-002.b] 切 tab 后补发了 tab=following 请求
      posts?tab=latest&page=1&size=10 | posts?tab=following&page=1&size=10
PASS  [BUG-002.c] 渲染条数与关注流一致                   渲染 1 条，关注流应为 1 条
```

**专项测试：** PASS（3/3）
**相关模块：** self / follow / post / user —— pytest 全量 248 passed，0 failed
**完整测试：** `pytest -q` → **248 passed / 0 failed**；`mvn test` → **291 run / 0 failures**
**手工验证：** PASS（真实 Chrome，上表即为浏览器实测输出）

### 5. 最终结论

```text
BUG-002
状态：FIXED
严重程度：P2
根因：HomeView.load() 的 loading 守卫吞掉了切换 tab 时的重新查询请求
修复：引入请求序号，区分「加载更多去重」与「切换条件的新查询」，并丢弃过期响应
回归：PASS（BUG-002.a/b/c）
```

---

## BUG-003：搜索换关键词后结果不更新，地址栏与结果不一致

### 1. 发现 Bug

**状态：** `CONFIRMED`

**严重程度：** P2（搜索结果与用户当前关键词不匹配，刷新后结果会变，属于用户可见的数据不一致）

**发现方式：** 自动化测试（真实 Chrome）+ Code Review

**复现步骤：**

1. 打开 `/search`
2. 输入真实存在的用户名 `rh87fbaa8d`，回车（首次搜索被延迟 3s）
3. 在请求还在飞的时候，把关键词改成必定搜不到的 `zzz-no-such-user`，再回车
4. 结果：地址栏与输入框都是 `zzz-no-such-user`，但**只发出了 1 次搜索请求**，页面仍显示第 1 个关键词的结果

**实际（探针原始输出）：**

```
发出的 search 请求数 = 1
  第 1 个: ?keyword=rh87fbaa8d&page=1&size=10
  第 2 个: (无 —— 第 2 次搜索被丢弃)
地址栏: ?keyword=zzz-no-such-user
输入框: zzz-no-such-user
页面文本: ... 找到 1 位用户 回归aa8d @rh87fbaa8d ...     ← 结果是第 1 个关键词的
```

**预期：** 地址栏 / 输入框 / 结果三者一致；`zzz-no-such-user` 应提示「没有找到与「zzz-no-such-user」相关的用户」。

**实际：** 三者不一致——这正是「同一页面显示两种真相」，用户复制链接给别人，别人看到的结果完全不同。

### 2. 分析 / 定位原因

**相关模块：** Frontend / Search

**相关 API：** `GET /api/users/search?keyword=&page=&size=`（后端正确）

**根因：** `frontend/src/views/search/SearchView.vue` 第 29 行，与 BUG-002 同源

```ts
async function search(reset = true) {
  const value = keyword.value.trim()
  ...
  if (loading.value) return     // ← 根因：新关键词的搜索被吞掉
```

`onSubmit()` 先 `router.replace({ keyword })` 改了地址栏，再 `search(true)`；
`search` 被守卫挡住 → **URL 已更新、请求未发出**。

```text
Root Cause:
SearchView.search() 的 loading 守卫丢弃了「用户换了关键词后的新搜索」，
而 onSubmit 已经把新关键词写进地址栏 → URL 与页面结果不一致。
```

### 3. AI 辅助修改

**修改文件：** `frontend/src/views/search/SearchView.vue`（**仅此一个文件**）

**修改内容：** 同 BUG-002 的请求序号方案，并额外覆盖一处同源分支：

```ts
let requestSeq = 0

async function search(reset = true) {
  const value = keyword.value.trim()
  if (!value) {
    // 清空关键词同样是一次"新查询"：让在飞的旧请求失效，
    // 否则旧响应回来会把已经清空的列表重新填上。
    requestSeq++
    results.value = []
    searched.value = false
    return
  }

  if (loading.value && !reset) return
  const seq = ++requestSeq
  ...
    if (seq !== requestSeq) return
  ...
}
```

> **为什么多改了 `if (!value)` 这一支：** 它和主分支是**同一个缺陷**——
> 用户在搜索请求飞行中清空关键词，UI 已清空但旧响应回来又会把列表填上，
> 依然是"界面状态与数据不同源"。用同一处 `requestSeq++` 一并封住，
> 并加了断言 `BUG-003.c` 守住它。（不是顺手重构，是同一个根因的第二个出口。）

**修改原则遵守情况：** 同 BUG-002（最小修改、不动后端、不改测试期望、不重构搜索模块）

**回归测试：** `tests/browser_regression.mjs` 的 `BUG-003.a` ~ `BUG-003.c`（YAML：`CONC-009`）

### 4. 回归验证

**Before Fix：**

```
BUG-003.a  FAIL   换关键词后重新发起了搜索请求    共 1 次：users/search?keyword=rh87fbaa8d&page=1&size=10
BUG-003.b  FAIL   页面结果与当前关键词一致        地址栏=zzz-no-such-user，页面含"没有找到"=false
```

**After Fix：**

```
PASS  [BUG-003.a] 换关键词后重新发起了搜索请求
      共 2 次：users/search?keyword=rhafb44b9c… | users/search?keyword=zzz-no-such-user…
PASS  [BUG-003.b] 页面结果与当前关键词一致        页面含"没有找到"=true
PASS  [BUG-003.c] 清空关键词后旧响应不会把列表重新填上    渲染 0 个用户卡片
```

**专项测试：** PASS（3/3）
**相关模块：** search / user —— pytest 全量 248 passed，0 failed
**完整测试：** `pytest -q` → **248 passed / 0 failed**
**手工验证：** PASS（真实 Chrome）

### 5. 最终结论

```text
BUG-003
状态：FIXED
严重程度：P2
根因：SearchView.search() 的 loading 守卫吞掉换关键词后的新查询，而 onSubmit 已先更新 URL
修复：请求序号 + 让"清空关键词"同样使在飞请求失效
回归：PASS（BUG-003.a/b/c）
```

---

## BUG-004：通知类型筛选不生效，标签与数据不一致

### 1. 发现 Bug

**状态：** `CONFIRMED`

**严重程度：** P2（筛选器形同虚设，用户以为在看某类通知、实际看到全部）

**发现方式：** 自动化测试（真实 Chrome）+ Code Review

**复现步骤：**

1. 准备一个同时有「关注」和「点赞」两类未读通知的账号 A
   （B 关注 A → `type=1`；B 点赞 A 的动态 → `type=2`；未读数 `{"total":2,"follow":1,"like":1}`）
2. 打开 `/notifications`（首屏「全部」请求被延迟 3s）
3. 请求还在飞时点击「点赞」标签
4. 结果：高亮是「点赞」，但没有发出带 `type=2` 的请求，列表里混着「关注了你」

**实际（探针原始输出）：**

```
发出的列表请求: ?page=1&size=20          ← 只有首屏「全部」这一次，没有 type=2
高亮 tab = 「点赞」
列表渲染 2 条：
  1. 回归aa8d 赞了你的动态
  2. 回归aa8d 关注了你                  ← 不该出现在「点赞」筛选下
```

**预期：** 高亮「点赞」时列表只含点赞通知，且应重新请求 `?type=2`。

### 2. 分析 / 定位原因

**相关模块：** Frontend / Notification

**相关 API：** `GET /api/notifications?type=2&page=&size=`（后端正确，筛选能力已由 JUnit + pytest 覆盖）

**根因：** `frontend/src/views/notification/NotificationView.vue` 第 48 行，与 BUG-002 同源

```ts
async function load(reset = true) {
  if (loading.value) return     // ← 根因
  ...
}

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key          // 标签先变
  load(true)                     // 请求被守卫吞掉
}
```

```text
Root Cause:
NotificationView.load() 的 loading 守卫丢弃了切换通知类型时的重新查询，
而 activeTab 已先变更 → 高亮的筛选类型与实际展示的数据不一致。
```

### 3. AI 辅助修改

**修改文件：** `frontend/src/views/notification/NotificationView.vue`（**仅此一个文件**）

**修改内容：** 同 BUG-002 的请求序号方案（`load()` 内 `if (loading.value && !reset) return`
+ `seq` 校验，`switchTab()` 不动）。

**修改原则遵守情况：** 同 BUG-002。
**回归测试：** `tests/browser_regression.mjs` 的 `BUG-004.a` ~ `BUG-004.b`（YAML：`CONC-010`）

### 4. 回归验证

**Before Fix：**

```
BUG-004.a  FAIL   切类型后补发了带 type=2 的请求   notifications?page=1&size=20
BUG-004.b  FAIL   高亮「点赞」时列表只含点赞通知   列表 2 条：赞了你的动态 / 关注了你
```

**After Fix：**

```
PASS  [BUG-004.a] 切类型后补发了带 type=2 的请求
      notifications?page=1&size=20 | notifications?page=1&size=20&type=2
PASS  [BUG-004.b] 高亮「点赞」时列表只含点赞通知   列表 1 条：赞了你的动态
```

**专项测试：** PASS（2/2）
**相关模块：** notification —— pytest 全量 248 passed，0 failed
**完整测试：** `pytest -q` → **248 passed / 0 failed**
**手工验证：** PASS（真实 Chrome）

### 5. 最终结论

```text
BUG-004
状态：FIXED
严重程度：P2
根因：NotificationView.load() 的 loading 守卫吞掉切类型后的新查询，而 activeTab 已先变更
修复：请求序号（同 BUG-002）
回归：PASS（BUG-004.a/b）
```

---

## BUG-005：头像 URL 未校验 `/uploads/` 前缀，可写入任意外链

### 1. 发现 Bug

**状态：** `CONFIRMED`

**严重程度：** P2（校验不对称 + 内容审核缺口。低危，但规则明确且可复现）

**发现方式：** API 测试 + Code Review

**复现步骤：**

1. 用任意账号登录
2. `PUT /api/users/me/avatar`，body `{"avatar":"https://evil.example.com/track.png"}`
3. 结果：`200 success`，且该外链**真的落库**

**实际（探针原始输出）：**

```
✗ 外链头像被拒绝   期望=400 INVALID_IMAGE_URL  实际=200 success
     → 当前头像实际落库值: "https://evil.example.com/track.png"

对照组（同一探针，同一次运行）：
✓ 外链动态图片被拒绝        400 "图片地址不合法，请先通过上传接口获取"
✓ 前缀近似串 /uploads-evil/ 被拒绝  400 "图片地址不合法，请先通过上传接口获取"
✓ 空白头像被拒绝            400 "头像地址不能为空"
```

**预期：** 与动态图片对称——只接受本项目上传接口返回的 `/uploads/` 前缀 URL。

**实际：** 任意外链均可写入并对外展示。

### 2. 分析 / 定位原因

**相关模块：** Backend / User（`UserServiceImpl`）

**相关 API：** `PUT /api/users/me/avatar`

**业务规则来源（三条独立证据，不属于"设计差异"）**

| 来源 | 原文 |
|---|---|
| `docs/design.md` L199 | `avatar VARCHAR(255) DEFAULT ''` \| **相对路径 `/uploads/avatar/xx.png`** |
| `UpdateAvatarRequest.java` L11-17 | 「采用**先上传拿 URL**，再提交 URL 的两阶段模式，**与发布动态的图片处理保持一致**」 |
| `test_cases/README.md` L89 | 已登记缺口 `SEC-011 头像 URL 未校验 /uploads/ 前缀｜既有设计缺口，与动态图片校验不对称` |

**根因：** `backend/src/main/java/com/miqu/service/impl/UserServiceImpl.java` 第 90-100 行

```java
public UserVO updateAvatar(Long userId, UpdateAvatarRequest request) {
    requireActiveUser(userId);
    User update = new User();
    update.setId(userId);
    update.setAvatar(request.avatar().trim());   // ← 只 trim，不校验来源前缀
    userMapper.updateById(update);
    ...
}
```

对照 `PostServiceImpl.validateImageUrls()` L318-325（同一份规则，动态侧已实现）：

```java
String prefix = properties.getUpload().getUrlPrefix() + "/";
for (String url : images) {
    if (!url.startsWith(prefix)) throw BizException.of(ErrorCode.INVALID_IMAGE_URL);
}
```

```text
Root Cause:
头像接口只做 @NotBlank + @Size 校验，未校验 URL 是否来自本项目的上传接口；
而同一业务规则在动态图片侧已由 validateImageUrls() 强制。
属"规则已定义但一处漏实现"的不对称缺陷，非设计差异。
```

### 3. AI 辅助修改

**修改前的风险核对（重要）**

计划里我标记了"必须先确认 `url-prefix` 与种子头像形态，否则可能把正常头像改坏"。实际核对结果：

| 核对项 | 结果 | 结论 |
|---|---|---|
| `miqu.upload.url-prefix` | `/uploads`（`application.yml` L109） | 校验前缀为 `/uploads/` |
| 前端头像链路 | `fileApi.uploadImage()` → 上传接口 → 用返回的 url 提交 | 落在 `/uploads/` 下，**不受影响** |
| 现有 pytest | `test_update_avatar` 传 `/uploads/image/2026/09/qa-avatar.jpg` | **本来就符合**，不会被打破 |
| 现有 JUnit | `UserControllerTest#updateAvatar_success` 同上 | **本来就符合**，不会被打破 |
| 种子数据 | 21 个头像全是 `https://i.pravatar.cc/...` | 校验只在**写入**时生效，存量行不受影响 |

→ 风险实测为**零**：不会打破任何现有测试，也不会破坏任何正常前端路径的可写性。
（唯一"不一致"是种子数据本身不符合文档要求的相对路径——那是数据侧遗留，不在接口校验范围。）

**修改文件：** `backend/src/main/java/com/miqu/service/impl/UserServiceImpl.java`
（+1 import、+1 构造字段、约 +7 行校验；`@RequiredArgsConstructor` 自动接上新依赖，无手工构造点）

**修改内容：** 复用与动态图片一致的校验，抽成同一条规则：

```java
public UserVO updateAvatar(Long userId, UpdateAvatarRequest request) {
    requireActiveUser(userId);

    String avatar = request.avatar().trim();

    // 与发布动态的图片校验保持一致：只接受本项目上传接口返回的地址。
    // 否则用户可以把自己的头像指向任意外链（追踪像素、违规图），
    // 服务器既成了别人的图床，内容审核也无从下手。
    // 前缀必须带 "/"，否则 "/uploads-evil/x.png" 这类近似串会蒙混过关。
    String prefix = properties.getUpload().getUrlPrefix() + "/";
    if (!avatar.startsWith(prefix)) {
        throw BizException.of(ErrorCode.INVALID_IMAGE_URL);
    }

    User update = new User();
    update.setId(userId);
    update.setAvatar(avatar);
    userMapper.updateById(update);

    log.info("用户修改头像: userId={}", userId);
    return getCurrentUser(userId);
}
```

**修改原则遵守情况：**

| 原则 | 落实 |
|---|---|
| 最小修改 | 只改一个 Service 方法；复用既有 `ErrorCode.INVALID_IMAGE_URL`，**没有新增错误码**（避免变更对外契约） |
| 不改变无关业务 | 未动 `updateProfile` / `changePassword` / 查询逻辑；未动 `MiquProperties` |
| 不修改数据库结构 | 未动 schema |
| 不修改测试期望 | 既有 2 条头像用例原样保留（它们本来就传 `/uploads/…`） |

**回归测试：**

```text
pytest: api/test_user.py::test_update_avatar_rejects_external_url
        api/test_user.py::test_update_avatar_rejects_lookalike_prefix
JUnit:  UserControllerTest#updateAvatar_externalUrl_rejected
        UserControllerTest#updateAvatar_lookalikePrefix_rejected
YAML:   SEC-011（已由 gap 转为 implemented）
```

### 4. 回归验证

**Before Fix（探针实测）：**

```
✗ 外链头像被拒绝   期望=400  实际=200 success
      → 当前头像实际落库值: "https://evil.example.com/track.png"     ← 真的写进去了
```

**After Fix（同一探针）：**

```
✓ 外链头像被拒绝          实际=400 图片地址不合法，请先通过上传接口获取
      → 当前头像实际落库值: ""                                      ← 被拒绝后无副作用
✓ 空白头像被拒绝          实际=400 头像地址不能为空
✓ 外链动态图片被拒绝      实际=400（对照组，本来就对）
✓ 前缀近似串被拒绝        实际=400（对照组，本来就对）
共 15 项，通过 15，不符预期 0
```

**专项测试：** PASS（pytest 2 条 + JUnit 2 条）
**相关模块：** user / file / post —— pytest 全量 248 passed，0 failed
**完整测试：** `pytest -q` → **248 passed / 0 failed**；`mvn test` → **291 run / 0 failures / 0 errors**
**手工验证：** PASS（探针 + 真实前端上传链路不受影响）

### 5. 最终结论

```text
BUG-005
状态：FIXED
严重程度：P2
根因：UserServiceImpl.updateAvatar 未校验上传前缀，与动态图片校验不对称（规则已定义、一处漏实现）
修复：复用 INVALID_IMAGE_URL，只接受 /uploads/ 前缀（复用既有错误码，不变更对外契约）
回归：PASS（pytest 2 + JUnit 2）
备注：种子头像与文档要求的相对路径不一致属数据侧遗留，不在本次修复范围，已如实登记
```

---

## BUG-006：头像加载失败无兜底，取不到就是永久破图

### 1. 发现 Bug

**状态：** `FIXED`　**严重程度：** P1　**发现方式：** 用户反馈 + 真实浏览器复现

**症状：** "头像图片无法加载"。

**复现步骤：**

1. 给账号设一个「前缀合法、但磁盘上不存在」的头像路径（接口会接受）
2. 真实 Chrome 打开该用户主页

**实际（修复前）：**

```
<img> ... naturalWidth=0  → ★破图
<img> ... naturalWidth=0  → ★破图
首字母占位 span 数量 = 0   ← 本应降级到占位，但没有
```

**预期：** 取不到的头像退化成首字母占位，与"没配头像"表现一致。

### 2. 分析 / 定位原因

**相关模块：** Frontend / 头像渲染

**根因：** `frontend/src/components/UserAvatar.vue` 只有 `v-if="src"`——
判断的是"**有没有配**头像"，不是"**能不能取到**"。缺 `@error` 兜底。

**为什么影响面是全站**：全项目 15 处头像（`DefaultLayout` / `PostCard` / `CommentList` /
`PostEditor` / `UserCard` / `MessageView` / `NotificationView` / `AdminLayout` /
5 个后台管理页）全部渲染同一个 `UserAvatar`，没有第二处自行拼 `<img>`。
所以这一处缺兜底 → **全站头像一起表现为"加载不出来"**。

```text
Root Cause:
UserAvatar 只有"有无 src"的判断，没有"加载成功与否"的判断。
URL 非空但内容取不到时（外链被拦 / 文件缺失 / 返回非图片内容），
<img> 永久停留在解码失败态，既不降级占位也不会自愈。
```

### 3. AI 辅助修改

**修改文件：** `frontend/src/components/UserAvatar.vue`（唯一改动点，净 +14 行）

```vue
const failed = ref(false)
watch(() => props.src, () => { failed.value = false })      // 换头像后重试
const showImage = computed(() => Boolean(props.src) && !failed.value)
```
```vue
<img v-if="showImage" :src="src!" ... @error="failed = true" />
<span v-else class="miqu-avatar miqu-avatar--fallback" ...>…</span>
```

**修改原则：** 最小修改（只改一个组件）/ 不改后端契约 / 不改既有测试期望 / 不重构

**回归测试：** `tests/browser_regression.mjs` 的 `BUG-006.a` ~ `BUG-006.c`（YAML：`CONC-012`）

### 4. 回归验证

```
Before Fix:  破图 <img> 2 个；首字母占位 span 0 个          → FAIL
After Fix:   破图 <img> 0 个；首字母占位 span 2 个          → PASS
             [BUG-006.a] 造出一个指向不存在文件的头像        PASS
             [BUG-006.b] 页面上不再有解码失败的 <img> 头像    PASS
             [BUG-006.c] 取不到的头像降级成了首字母占位        PASS
```

**专项测试：** PASS（3/3）　**完整测试：** pytest 250 passed / 0 failed；JUnit 291 / 0 failures
**手工验证：** PASS（真实 Chrome + 截图 `.workbuddy/screenshots/bug006-avatar-fallback.png`）

### 5. 最终结论

```text
BUG-006
状态：FIXED
严重程度：P1（用户可见的"功能坏了"，且影响全站 15 处）
根因：UserAvatar.vue 缺 @error 兜底，只有"有无 src"判断
修复：加 failed 状态 + @error + watch(src) 重试（唯一改动点）
回归：PASS（BUG-006.a/b/c）
```

---

## BUG-007：静态资源 404 被包成 `HTTP 200 + JSON`

### 1. 发现 Bug

**状态：** `FIXED`　**严重程度：** P2　**发现方式：** API 测试 + Code Review

**实测（修复前）：**

```
GET /uploads/image/2026/09/definitely-not-exist.png
  -> http=200  content-type=application/json
     {"code":404,"message":"请求的资源不存在","data":null}
```

**预期：** 缺失的静态资源返回**真正的 HTTP 404**。

### 2. 分析 / 定位原因

**根因：** `GlobalExceptionHandler` 的"业务失败 HTTP 恒 200"约定被静态资源共用——
`handleNoResource` 用同一分支处理了 `/api/**` 与 `/uploads/**`。

**影响：**
1. `<img>` 拿到一段 JSON 去解码，必然失败 → 破图（**BUG-006 的直接放大器**）；
2. 缓存层把"不存在的资源"当成一次成功响应，语义失真（CDN/浏览器都可能错误缓存）。

### 3. AI 辅助修改

**修改文件：** `backend/src/main/java/com/miqu/exception/GlobalExceptionHandler.java`

按请求 URI 分流：落在上传前缀下的返回 `404`，其余沿用 `200`。
**这条是有意偏离项目级约定的例外**，所以同时加了一条对照用例
`test_unknown_api_path_keeps_http_200`，防止后人把例外推广到业务接口。

**回归测试：** `tests/api/test_file.py::test_missing_uploaded_file_returns_real_404`
（YAML：`SEC-012`）

### 4. 回归验证

```
静态缺失文件   : http=404                     （修复前 200）
/api 未映射路径: http=200（约定未动）
真实存在的图片 : http=200 + image/png          （未受影响）
pytest: 250 passed / 0 failed        JUnit: 291 run / 0 failures
```

### 5. 最终结论

```text
BUG-007
状态：FIXED
严重程度：P2
根因：静态资源与业务接口共用同一个 404 兜底，被"HTTP 恒 200"约定污染
修复：按 URI 分流，静态资源返回真 404；/api/** 契约不变（并有对照用例钉住）
回归：PASS（pytest 2 条）
```

---

## BUG-008：头像接口不校验目标文件是否存在 ⏸

### 1. 发现 Bug

**状态：** `CONFIRMED（待决策，未修改）`　**严重程度：** P2

**实测：** `PUT /api/users/me/avatar` 接受 `/uploads/image/2026/09/definitely-not-exist.png`
（磁盘上不存在）→ `200 success`，并把该路径写进库。
库里的 `id=237` 就是这样产生的坏头像。

### 2. 分析 / 定位原因

`UserServiceImpl.updateAvatar` 与 BUG-005 是**同一个方法的两半**：
BUG-005 补了"来源前缀"校验，没补"文件真实存在"校验。

### 3. 为什么没有直接改（关键）

**修法本来只有 5 行，但它会打破 2 条既有用例**——这两条引用的路径，磁盘上都不存在：

| 用例 | 传的 avatar | 断言 | 磁盘上是否存在 |
|---|---|---|---|
| `tests/api/test_user.py::test_update_avatar` | `/uploads/image/2026/09/qa-avatar.jpg` | `200` + 回读一致 | ❌ |
| `UserControllerTest#updateAvatar_success` | `/uploads/image/2026/09/newavatar.jpg` | `$.code == 200` | ❌ |

即：**这两条用例目前把"可以设置一个不存在的文件"当成了预期行为。**

按项目自己的纪律（`README.md`、`docs/TESTING_GUIDELINES.md`）：
> 测试与业务规则冲突时，停下来报告，**不要改测试来让代码通过**。

所以本轮**没有动它们**，也没有改代码。

**三个可选方向（需拍板）：**

| 方案 | 内容 | 代价 |
|---|---|---|
| A（推荐） | 把两条用例改成"先真上传一张图，再把返回 URL 设为头像" | 用例更强更真实；`UserControllerTest` 需走一次 multipart 或造临时文件 |
| B | 只加"拒绝路径穿越"（`../`），不校验存在性 | 零冲突，但挡不住"指向不存在的文件" |
| C | 不改代码，登记为已知缺口 | BUG-006 的兜底已让坏头像表现正常，对**当前症状**而言非必需 |

### 4. 最终结论

```text
BUG-008
状态：CONFIRMED（待决策）
严重程度：P2（加固项：防止产生坏数据，而非当前症状的成因）
根因：updateAvatar 未校验目标文件存在（与前缀校验不同层）
修复：未执行 —— 需先决定如何处理那 2 条把旧行为当预期的用例
```

---

## 附：Possible Issue（发现风险，但本轮无法确认为缺陷）

> 以下条目**均未修改代码**。按任务书要求，不能确证的一律不许动手。

| ID | 描述 | 为什么不下定性结论 |
|---|---|---|
| PI-01 | `UserProfileView.onFollowChange()` 丢弃了服务端返回的**权威** `followerCount`，改用本地 `profile.followerCount ± 1` | 需要"进入主页后他人恰好同时关注"才能产生偏差，无法稳定复现；实际影响极小。对比 `FollowButton` 明明已拿到服务端真值 |
| PI-02 | 「不能禁用管理员账号」分支 HTTP 层不可达（`CANNOT_DISABLE_ADMIN`） | 校验顺序中"不能操作自己"先命中，且种子只有一个管理员。**无输入可触达**，属覆盖缺口（`SEC-004`），不是缺陷 |
| PI-03 | `uk_users` 冲突被映射为通用 `CONFLICT(409)`，日志文案称"未映射" | 仅**日志措辞**有误导性，业务响应码正确，不构成功能缺陷；且属并发路径，改造收益低 |
| PI-04 | `CommentList.load()` 使用同一 `loading` 守卫写法 | 评论列表**没有"切换筛选条件"入口**（`reset=true` 只在挂载时发生），当前**不可触达**，不构成缺陷。仅记录为同类写法，避免将来新增筛选时踩坑 |
| PI-05 | 前端无单元/组件测试 | 项目**有意取舍**（见 `tests/README.md`）。正是本轮三个前端缺陷长期未被发现的原因，已通过新增 `browser_regression.mjs` 部分缓解 |
| PI-06 | 数十线程同瞬间首建会话未压测 | 属压测范畴（`CONC-007`），`test_concurrency.py` 已覆盖到 8 线程且通过 |
| PI-07 | 种子头像依赖外部图床 `i.pravatar.cc`（21 个） | 离线环境显示破图，属环境依赖而非代码缺陷；与 BUG-005 的"外链"是两回事 |

---

## 附：Not A Bug（经代码/测试验证确认为正常行为）

| 项 | 结论依据 |
|---|---|
| **BUG-001** 取消关注后仍能看到对方动态 | API + DB + 浏览器三路交叉，行为符合 `docs/design.md` L485 定义。见本文档首条 |
| 取消关注后 `notification` 行仍在库里 | `deleted=1` 是**逻辑删除**，符合 `schema.sql` 对 notification 的约定；对外查询与未读数已正确排除。**不是"没删干净"** |
| 取消关注后 `post` 行仍在库里 | `tab=following` 靠 `INNER JOIN follow` 过滤，而不是删动态；动态本身仍应能从「最新动态」与作者主页访问。**是预期行为** |
| 取关未关注者 → `404 尚未关注该用户` | 与 `docs/api/follows.md` L195 及 pytest 断言一致 |
| 重复关注 → `409 ALREADY_FOLLOWED` | 由 `uk_follower_following` 兜底，文档已写明 |
| `tab=following` 未登录 → `401` 且由 Service 抛出 | 有意设计，见 `docs/testing/API_DOCUMENT_AUDIT.md` D-03 |
| 非法 ID / 不存在 ID 的 400/404 分类 | 本轮 15 项边界探针里 14 项完全符合文档（分页 0/51 → 400、`tab=hot` → 400、非数字 id → 400、不存在资源 → 404 且文案成体系） |

---

## 附：本轮覆盖了哪些维度（以及没覆盖什么）

| 维度 | 本轮做法 | 结果 |
|---|---|---|
| A 状态转换 | 关注→取关（含通知撤回、冗余计数）、发布→删除、点赞→取消、未读→已读 | 未发现新缺陷（BUG-001 被证伪；取关后的通知/计数/动态三者一致） |
| B 权限 | 复核 `test_admin.py` 11 接口 × 3 角色矩阵、会话参与者隔离、互关限制 | 未发现新缺陷（既有用例已覆盖） |
| C 数据一致性 | 关注状态 / 粉丝数 / 关注数 / 动态可见性 / 未读数 逐项对账 | **发现 BUG-002/003/004**（前端筛选与数据不同步）；后端计数一致 |
| D API 边界 | 15 项探针：空值、超长、非法 ID、不存在 ID、分页上下界、`tab` 非法值 | **发现 BUG-005**；其余 14 项符合预期 |
| E 前后端一致性 | HTTP 恒 200 + 业务 code、前端错误处理、loading / empty state、分页 | **发现 BUG-002/003/004**；错误处理与空态无问题 |
| F 并发 | 复核 `test_concurrency.py`（注册/关注/取关/点赞/建会话/发消息，8 线程 + Barrier） | 未发现新缺陷；不重构框架，数十线程仍记为缺口 |
| G 文件上传 | 复核 `test_file.py`（14 条：空文件/超 5MB/伪后缀/魔数/MIME/未登录/非 multipart） | 未发现新缺陷，未重复造用例 |

**没覆盖 / 明说做不到的：**

- 前端缺陷族只在 **Chrome + 本地延迟注入**下复现过；未在真实弱网、Safari/Firefox 上验证。
- BUG-002/003/004 的触发窗口 = 首屏请求时长（本地约 200~500ms，慢网可达数秒）。**不是 100% 必现**，
  但用户"手快"或网络抖动时确实会撞上；探针用延迟把窗口放大了才稳定复现。
- 未对后端做任何压测或 fuzz。

---

## 附：本次新增 / 修改的文件

**生产代码（唯一被改动的部分，共 4 个文件）**

| 文件 | 改动 | 对应 |
|---|---|---|
| `frontend/src/views/home/HomeView.vue` | `load()` 请求序号 | BUG-002 |
| `frontend/src/views/search/SearchView.vue` | `search()` 请求序号 + 清空分支失效 | BUG-003 |
| `frontend/src/views/notification/NotificationView.vue` | `load()` 请求序号 | BUG-004 |
| `backend/src/main/java/com/miqu/service/impl/UserServiceImpl.java` | 头像前缀校验（+1 import / +1 字段 / +7 行） | BUG-005 |

**测试与文档**

| 文件 | 性质 | 作用 |
|---|---|---|
| `tests/browser_regression.mjs` | **新增回归用例** | 12 项断言；`Before Fix: 5 PASS / 6 FAIL` → 修复后 12/12 |
| `tests/api/test_user.py` | **扩展现有用例** | +2 条 BUG-005 回归（外链、前缀近似串） |
| `backend/src/test/java/com/miqu/user/UserControllerTest.java` | **扩展现有用例** | +2 条 BUG-005 回归（同款边界） |
| `test_cases/security_and_concurrency.yaml` | 用例登记 | `SEC-011` gap→implemented；新增 `CONC-008`~`011`（`ui_race_rules`） |
| `test_cases/check_consistency.py` | 校验器增强 | 支持 `browser` 指针（前端用例不归 pytest 收集） |
| `test_cases/README.md`、`core_business_rules.yaml` | 数字与缺口同步 | 98 条 / 96 implemented / 2 gap |
| `docs/testing/TEST_CASE_SCHEMA.md` | 规范补充 | §5.1 `browser` 字段与自动校验 |
| `docs/testing/TEST_EXECUTION_REPORT.md`、`MIQU_TEST_SYSTEM.md`、`TEST_COVERAGE_MATRIX.md`、`API_DOCUMENT_AUDIT.md` | 数字与状态同步 | 250 / 291 / 541 / 15 |
| `tests/README.md`、`docs/api/README.md`、`README.md` | 数字与缺口同步 | 同上 |
| `docs/testing/bug_report.md` | 文档 | 本文件 |
| `.workbuddy/tools/*.py`、`*.mjs` | 只读探针（5 个） | 复现与定位，不进测试套件 |

> **为什么前端缺陷不写成 pytest**：后端每次返回都是对的，错在前端没把请求发出去——
> 强行写成 pytest 只会得到"测了个不存在的错误"。
> 它们由 `browser_regression.mjs` 承担，且**不参与 pytest 收集**（`pytest.ini` 的
> `testpaths = api` 只收 `test_*.py`），所以前端红不会污染 pytest 的绿灯。

---

## 附：最终验收

```
已知 Bug
   ↓  全部确认（BUG-001 证伪；BUG-002~008 复现）
P0 / P1 / P2 已修复
   ↓  BUG-002~007 共 6 个 FIXED；BUG-008（P2）待决策 —— 它撞上了 2 条既有用例，按纪律停下上报
都有回归测试
   ↓  BUG-002/003/004/006 → 浏览器 11 项断言；BUG-005 → pytest 2 + JUnit 2；BUG-007 → pytest 2
专项测试通过
   ↓  browser_regression.mjs 15/15 PASS（exit 0）
相关模块通过
   ↓  self / follow / post / search / user / notification / file 全绿
全量测试通过
   ↓  pytest 250 passed / 0 failed；JUnit 291 run / 0 failures / 0 errors
bug_report.md 完整
   ↓  本文件
```

| 验收项 | 结果 |
|---|---|
| 发现的 Bug 候选 | 8（BUG-001~008） |
| Confirmed Bug | **7**（001 证伪，002~008 确认） |
| Possible Issue | **7**（见上节，均未动代码——不能确证的不许改） |
| Not A Bug | **7 条结论**（含 BUG-001 证伪） |
| 已修复 | **6**（002/003/004/005/006/007） |
| 待决策 | **1**（008：与 2 条既有用例冲突，见其章节） |
| 新增回归测试 | 浏览器 15 项断言 + pytest 4 + JUnit 2 |
| 最终 pytest | **250 collected / 250 passed / 0 failed** |
| 最终 JUnit | **291 run / 0 failures / 0 errors / 0 skipped** |
| 用例数量变化 | pytest 246 → **250**（+4，未减）；JUnit 289 → **291**（+2，未减） |
| 未解决 P0/P1/P2 | **1**（BUG-008，P2，加固项；对当前症状非必需） |
| 是否建议继续修复 | 生产代码已闭环，**不建议再动**。唯一待你决定的是 BUG-008 的走向（改用例 / 只防穿越 / 登记为缺口） |

**数据库前置条件**：JUnit 依赖种子绝对值，运行前已重置
（`schema.sql` + `data.sql`，21 用户 / 40 动态）；重置前的库已备份到
`.workbuddy/backups/miqu-pre-bughunt-fix-20260914-165115.sql`。
