# 测试编写规范（TESTING_GUIDELINES）

写给"要往 Miqu 里加用例"的人（包括未来的自己）。
**动手前先读这一页**，能省掉大半返工。

体系总览见 [`testing/MIQU_TEST_SYSTEM.md`](testing/MIQU_TEST_SYSTEM.md)。

---

## 0. 三条硬纪律（不可破）

### 1. 不许把测试改绿

测试红了，**先怀疑代码，不要先怀疑测试**。明确禁止：

- ❌ 删除用例
- ❌ `@Disabled` / `pytest.skip` 掉
- ❌ 放宽断言（`== 400` 改成 `in (400, 500)`、`assertTrue` 代替精确比较）
- ❌ 捕获异常后忽略（`try: ... except: pass`）
- ❌ 改测试数据去掩盖 Bug
- ❌ 把"期望 403"改成"期望 200"让测试通过

**唯一合法的"红转绿"路径**见 §5（种子数据漂移）。

### 2. pytest 断言用差值，不用绝对值

写操作会**真实落库且不回滚**，库里数据随运行次数增长。

```python
# ✗ 依赖全局状态，第二次跑就挂
assert admin_client.get("/api/admin/stats").data["userTotal"] == 21

# ✓ 只断言"这次操作带来的变化"
before = admin_client.get("/api/admin/stats").data["userTotal"]
anonymous.register(f"qa{unique_suffix}")
after = admin_client.get("/api/admin/stats").data["userTotal"]
assert after == before + 1
```

### 3. 写数据只用当次运行新建的随机用户

```python
# ✗ 测试会真实建用户，且没有删除用户的接口 → 第二次运行撞唯一键
client = ApiClient(base_url); client.register("qa_test_user")

# ✓ 用夹具，用户名自带随机后缀
def test_xxx(fresh_user):
    client = fresh_user()
```

不要去改种子账号（`test001` / `admin` / `banned001` …），
否则会污染其他依赖种子数据的用例。

---

## 1. 选哪套测试

| 你要验的东西 | 写在哪 |
|---|---|
| 纯业务分支、参数校验、异常分支 | **JUnit**（进程内、能回滚、快） |
| 序列化格式、过滤器链、事务是否提交、跨接口联调 | **pytest**（真实 HTTP） |
| 并发竞态 | **pytest**（`concurrency` 用例，需要真实事务） |
| 两侧都值得钉死的关键规则 | **两套都写**（互关私聊就是这么做的） |

判断口诀：**"这条断言值不值一次真实的 HTTP 往返？"**
值 → pytest；不值 → JUnit。

---

## 2. 后端有一条贯穿式约定：HTTP 恒 200

`GlobalExceptionHandler` 没有 `@ResponseStatus`，因此**业务失败也是 HTTP 200**，
真正的结果在响应体的 `code` 里。

```python
# ✗ 这条断言永远为真，等于没断言
assert response.status_code == 200

# ✓ 用封装后的 ok / code
assert response.ok                 # code == 200
assert response.code == 403
```

pytest 侧已由 `tests/utils/client.py` 固化这一点（`ApiResponse.ok` / `.code`）。
**唯一例外**：`GET /api/health` 在数据库挂掉时 `code=500`（HTTP 仍 200）。

---

## 3. 断言强度：精确还是宽松

Bean Validation 对同一字段可能同时触发多条约束（空用户名会同时违反
`@NotBlank`、`@Size`、`@Pattern`），而校验器**不保证触发顺序**。因此：

| 情况 | 断言方式 |
|---|---|
| 能构造出"只违反一条约束"的输入 | 断言**精确文案**（`response.message == "参数校验失败"` 之类） |
| 无法避免多条约束同时命中 | 只断言**错误码**，并在用例里注明原因 |

这样测试既严格，又不会因为校验器内部顺序变化而假失败。

**不要为了省事一律只断言错误码**——那会漏掉"错误码对但文案错"的回归。

---

## 4. 命名与结构

- 测试函数名用**英文、说清行为**：`test_one_way_follow_cannot_send`，
  不要 `test_follow_2`。
- 文件名 `test_<模块>.py` / `<模块>ControllerTest.java`。
- 用例里保留**中文注释说明"为什么"**——尤其是"为什么这条必须存在"
  （防止后人以为多余而删掉）。
- 用例之间必须**可独立失败**：不要依赖"上一条用例先跑过"。

### pytest 标记（`pytest.ini` 已定义）

| 标记 | 含义 |
|---|---|
| `@pytest.mark.smoke` | 冒烟主流程，应始终通过 |
| `@pytest.mark.read` | 只读，不修改任何数据，可随时重跑 |
| `@pytest.mark.write` | 会往库里写数据 |

新增用例请**至少**归类到 `read` 或 `write`，冒烟路径再加 `smoke`。

---

## 5. 种子数据漂移：唯一合法的"测试红了"处理

少量用例保留了对**种子数据的绝对值断言**（如"test001 有 14 个粉丝"、
"会话 1 有 2 条未读"）。它们只在**种子被手工改动后**才失效。

**症状**：一批看起来无关的用例同时失败，失败信息是
`assert 15 == 14` 这种"差一两个"。

**正确处置**（这不是"改测试"，是恢复环境）：

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql
```

**错误处置**：把 `14` 改成 `15`。那等于用测试去迎合被污染的环境。

> 已被这个坑咬过一次：手工用前端改过 `test001` 的昵称后，
> 搜索与粉丝数相关用例一起变红。重置数据库后 246 条全绿。

---

## 6. 后端改动后必须重启

pytest 打的是**运行中的后端**。改了后端代码但没重新打包/重启，
跑的还是旧进程，会出现"改了但没生效"的假失败。

```bash
cd backend && mvn -DskipTests package
# 停掉旧进程，再启动
java -jar target/miqu-backend-1.0.0.jar --server.port=8081
```

排查失败用例时，先问这两句：

1. **是不是种子数据被改动了？** → 重置数据库
2. **是不是后端没重启？** → 重新打包并重启

这两条能解决绝大多数"莫名其妙"的失败。

---

## 7. 新增接口时的清单

1. 写 JUnit 用例覆盖分支（含异常分支）。
2. 写 pytest 用例覆盖端到端（至少：成功、未登录 401、非法参数）。
3. 如果接口需要登录，**确认它不在白名单里**（白名单是"失败关闭"策略：
   不登记即受保护）。
4. 在 `docs/api/<模块>.md` 补接口文档。
5. 若涉及**冻结规则或安全边界**，在 `test_cases/` 登记一条 YAML（见 `TEST_CASE_SCHEMA.md`）。
6. 跑一遍全量：`pytest -q` 与 `mvn test`，确认 0 失败。

---

## 8. 并发用例怎么写

项目里没有引入并发测试框架，用的是标准库，够用：

```python
from concurrent.futures import ThreadPoolExecutor
import threading

def _race(workers, call):
    # 用 Barrier 把线程压到同一时刻起跑，否则"并发"常常退化成串行
    barrier = threading.Barrier(workers)
    def run(i):
        barrier.wait()
        return call(i)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        return [f.result() for f in (pool.submit(run, i) for i in range(workers))]
```

两个要点：

1. **每个线程用一个独立的 `requests.Session`**
   （`ApiClient` 的 `session` 不保证线程安全，用 `_clone(client)` 派生）。
2. **断言"最终数据只有一份"**，而不是断言"某个线程返回了什么"：
   并发关注 → 恰好 1 行、粉丝数只 +1；并发建会话 → 收敛为同一个 id。

---

## 9. 提交前自检

- [ ] `pytest --collect-only -q` 的条数符合预期（新增了就 +N）
- [ ] `pytest -q` 全绿
- [ ] `mvn test` 全绿（改过后端时）
- [ ] 连跑两次 `pytest -q`，结果一致（幂等）
- [ ] 没有新增 `skip` / `xfail` / 被注释掉的断言
- [ ] 新增的 `write` 用例不会破坏别人对种子数据的断言
