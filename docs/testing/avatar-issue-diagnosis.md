# 头像无法加载 —— 诊断与修复报告

> 诊断时间：2026-09-16 · 修复时间：2026-09-16 19:1x
>
> **本轮修复了 BUG-006 / BUG-007；BUG-008 因与两条既有用例冲突，按纪律停下待决策**（见第六节）。
>
> 探针：`.workbuddy/tools/avatar_diagnose.mjs`、`avatar_e2e_probe.mjs`、
> `avatar_broken_fallback_probe.mjs`、`check_seed_avatars.mjs`

---

## 一、结论（先说答案）

**头像功能的代码链路本身是好的**——注册、上传、设为头像、后端返回图片、浏览器解码，全程实测通过。

"头像图片无法加载"的直接原因是这一条：

> **图片加载失败时，前端没有任何兜底。**
> `UserAvatar.vue` 只判断 `src` **有没有值**，不判断**能不能加载出来**。
> 所以只要 URL 非空但取不到图片，就是**永久破图**：既不显示首字母占位，
> 也不会在恢复后自动重试。

而且**全项目 15 处头像渲染共用这一个组件**（`DefaultLayout` / `PostCard` / `CommentList` /
`MessageView` / `NotificationView` / 5 个后台页面 …），所以这一处出问题，**全站头像一起"加载不出来"**。

叠加的放大因素（后端）：

> `/uploads/**` 下的文件不存在时，后端按"业务失败 HTTP 恒 200"的约定返回
> **HTTP 200 + `application/json` + `{"code":404,...}`**。
> 浏览器 `<img>` 拿到 JSON 无法解码 → 破图；且因为是 200，连 CDN/浏览器缓存的
> "失败不缓存"语义都失真了。

---

## 二、已排除的可能（实测确认"这部分没问题"）

排查最怕方向跑偏，所以先把排除项列清楚：

| 怀疑点 | 实测结论 | 证据 |
|---|---|---|
| 后端没起 / 接口挂了 | 排除 | `GET /api/health` → `code=200, database=UP` |
| `/uploads/**` 静态映射坏了 | 排除 | 真实存在的文件返回 `200 image/png`，内容正确 |
| 上传接口挂了 | 排除 | `POST /api/files/image` → `200`，返回 `/uploads/image/2026/09/8eb9f2b7….png` |
| 设为头像的接口挂了 | 排除 | `PUT /api/users/me/avatar` → `200`，`GET /users/me` 回读一致 |
| 前端代理没配 `/uploads` | 排除 | 经 5173 请求同一张图 → `200 image/png` |
| `vite preview` 不代理（vite.config 只配了 `server.proxy`） | **排除**（特意实测） | 4173 上 `/api/health` 与 `/uploads/...` **都通**——Vite 的 `preview.proxy` 默认继承 `server.proxy` |
| 20 个种子头像的外链图床挂了 | 排除（**当前网络下**） | 逐个拉取 20 个 `i.pravatar.cc` URL，全部 `200 image/jpeg`，3.4–7.5 KB |
| 空头像不会显示占位 | 排除 | 216 个空头像账号正常显示首字母占位 |
| 浏览器渲染/解码有问题 | 排除 | 真实 Chrome 里 `<img>` `naturalWidth=1`，解码成功 |

---

## 三、复现（实测到的真实缺陷）

**手法**：给测试账号设一个"前缀合法、但磁盘上并不存在"的头像路径，用真实 Chrome 打开该用户主页。

```
[1] 把头像设为 /uploads/image/2026/09/definitely-not-exist.png
    PUT -> code=200 success                     ← 接口接受了，没做存在性校验
[2] 直接请求该 URL（修复前）
    http=200  content-type=application/json
    body={"code":404,"message":"请求的资源不存在","data":null}
    ⚠ HTTP 是 200，返回的却是 JSON
[3] 真实 Chrome（修复前）
    <img> ... naturalWidth=0  → ★破图
    <img> ... naturalWidth=0  → ★破图
    首字母占位 span 数量 = 0   ← 本应降级到占位，但没有
[4] 真实 Chrome（修复后）
    破图 <img> 数量      = 0
    首字母占位 span 数量 = 2   ✓
```

截图：`.workbuddy/screenshots/avatar-broken-fallback.png`（修复前，空圆）、
`.workbuddy/screenshots/bug006-avatar-fallback.png`（修复后，首字母）

**库里已经有一个活生生的例子**：用户 `id=237 / qaa3f130b5aa`，
头像指向 `/uploads/image/2026/09/qa-avatar.jpg`，**该文件在磁盘上不存在**
（那是 pytest 用例 `test_update_avatar` 写进去的字符串，从来没有真正上传过文件）。
这个账号的头像就是破图——修复后它已经能优雅降级成首字母。

---

## 四、三个真实缺陷与处置

| 编号 | 缺陷 | 位置 | 级别 | 处置 |
|---|---|---|---|---|
| **BUG-006** | 头像加载失败无兜底：`<img>` 没有 `@error`，永不降级到首字母占位 | `frontend/src/components/UserAvatar.vue`（**全站 15 处共用**） | **P1** | ✅ **已修复** |
| **BUG-007** | 静态资源 404 被包成 `HTTP 200 + JSON`，`<img>` 拿到非图片内容 | `GlobalExceptionHandler`（静态资源走同一个兜底） | P2 | ✅ **已修复** |
| **BUG-008** | 头像接口不校验目标文件是否存在，可写入指向不存在文件的路径 | `UserServiceImpl.updateAvatar` | P2 | ⏸ **待决策**（与 2 条既有用例冲突，见第六节） |

> BUG-008 与上一轮修的 BUG-005 是**同一个方法的两半**：BUG-005 补了"来源前缀"校验，
> 没补"文件真实存在"校验。当时是有意收窄改动范围。

---

## 五、已完成的修复（最小改动）

### BUG-006：`UserAvatar.vue` 加失败兜底（净 +14 行）

```vue
const failed = ref(false)

/** 头像地址变了就重试——否则换成新头像后仍会停留在上一次的失败态。 */
watch(() => props.src, () => { failed.value = false })

const showImage = computed(() => Boolean(props.src) && !failed.value)
```

```vue
<img v-if="showImage" :src="src!" ... @error="failed = true" />
<span v-else class="miqu-avatar miqu-avatar--fallback" ...>…</span>
```

**为什么改这一处就够**：全站 15 处头像都渲染 `UserAvatar`，没有第二处自行拼 `<img>`。
`watch` 那一句是必要的——否则用户换成一张新头像后，会一直停留在上一次的失败态。

### BUG-007：静态资源 404 返回真状态码（+9 行，仅限 `/uploads/**`）

`GlobalExceptionHandler.handleNoResource` 按请求 URI 分流：

```java
Result<Void> body = Result.fail(ErrorCode.NOT_FOUND, "请求的资源不存在");

String staticPrefix = properties.getUpload().getUrlPrefix() + "/";
if (request != null && request.getRequestURI().startsWith(staticPrefix)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}
return ResponseEntity.ok(body);
```

**这一条有意偏离项目"HTTP 恒 200"的约定**，理由与边界：
- 静态资源不是业务接口，浏览器 `<img>` 与 CDN 靠**状态码**判断"取不到"；
- `/api/**` 的对外契约**完全没动**（仍返回 200 + body.code），
  并专门加了一条对照用例 `test_unknown_api_path_keeps_http_200` 防止后人把例外错误地推广出去。

实测三种情况：

```
GET /uploads/image/2026/09/definitely-not-exist.png  ->  HTTP 404   ✓（修复前是 200）
GET /api/definitely-not-exist                        ->  HTTP 200   ✓（约定未动）
GET /uploads/.../06f374ae….png（真实存在）           ->  HTTP 200 + image/png  ✓
```

### 回归验证（三层全绿）

```
浏览器回归:  15 项断言 / 15 PASS（修复前 BUG-006 三项为 FAIL）
pytest:      250 collected / 250 passed / 0 failed   （248 → 250，新增 2 条）
JUnit:       291 run / 0 failures / 0 errors / 0 skipped
前端:        vue-tsc 0 error、vite build 成功
用例一致性:  check_consistency.py exit 0（100 条 / 98 implemented / 2 gap）
```

新增用例：
- `test_cases/security_and_concurrency.yaml` → `SEC-012`、`CONC-012`
- `tests/api/test_file.py` → `test_missing_uploaded_file_returns_real_404`、`test_unknown_api_path_keeps_http_200`
- `tests/browser_regression.mjs` → `BUG-006.a/b/c`

---

## 六、BUG-008：为什么我停下来没改

**修法本来是 5 行**：在 `updateAvatar` 里解析出真实磁盘路径（并做目录穿越防护），
不存在就拒绝。

**但实测发现它会打破 2 条既有用例**——这两条用例引用的路径，磁盘上都不存在：

| 用例 | 传的 avatar | 断言 | 文件是否存在 |
|---|---|---|---|
| `tests/api/test_user.py::test_update_avatar` | `/uploads/image/2026/09/qa-avatar.jpg` | `200` + 回读一致 | ❌ 不存在 |
| `UserControllerTest#updateAvatar_success` | `/uploads/image/2026/09/newavatar.jpg` | `$.code == 200` | ❌ 不存在 |

也就是说：**这两条用例目前把"可以设置一个不存在的文件"当成了预期行为**。

按项目自己定的纪律（`docs/TESTING_GUIDELINES.md`、`README.md`）：
> 测试与业务规则冲突时，**停下来报告**，不要顺手改测试来让代码通过。

所以我没动它们。**要推进 BUG-008，需要你选一个方向**：

**方案 A（推荐）**：把这两条用例改成"先真的上传一张图，再把返回的 URL 设为头像"。
- 用例更真实（贴近前端实际链路），断言不变弱，只是换了输入来源。
- 代价：`UserControllerTest` 需要走一次 MockMvc multipart，或在测试里造一个临时文件。

**方案 B**：只加"拒绝路径穿越"这一半（`../` 之类），不校验存在性。
- 零冲突，但挡不住"指向不存在的文件"这一类。

**方案 C**：不改代码，把 BUG-008 正式登记为已知缺口。
- 理由也成立：文件可能事后被清理，写时校验并不能杜绝坏头像；
  而 BUG-006 的兜底已经让坏头像"表现正常"（退化成首字母），不再影响使用。

> 对**当前症状**而言 BUG-008 已经不是必需项——它是"防止产生坏数据"的加固。

---

## 七、另外两条数据侧收尾（未执行，等你确认）

1. `uploads/` 目录里 42 个文件**全是 pytest 造的 20/22/24 字节"魔术字节占位文件"**，不是真图。
   演示前建议清空该目录（后端会用同样的路径重新生成）。
2. 库里 `id=237` 的头像指向不存在的文件（就是 BUG-008 的产物）。
   它现在能优雅降级成首字母，**不清也不影响演示**；要清可以直接把 `avatar` 置空。

---

## 八、如果你那边仍然看到头像加载不出来

本机复现不出"种子头像全挂"，所以请按下面自查（本机实测这些路径都是通的）：

1. **地址栏必须是 `http://localhost:5173`**。Vite 6 只监听 IPv6，
   用 `http://127.0.0.1:5173` 会连不上（整站打不开，不只是头像）。
2. **看动态配图是否也挂**：动态图片来自 `picsum.photos`、种子头像来自 `i.pravatar.cc`，
   都是**外部图床**。两类一起挂 = 网络/代理拦了外链。
   —— 修复后这种情况**不再表现为破图**，而是显示首字母占位，这是预期行为。
3. **F12 → Network 筛 Img**：调 `i.pravatar.cc` 失败 → 外链网络问题；
   调 `/uploads/...` 返回 `application/json` → 那是修复前的老 jar，需要重新打包重启。
4. **只有个别账号头像异常** → 那是 BUG-008 产生的坏数据，现在会降级成首字母。
