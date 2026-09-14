# 系统 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`HealthController` / `HealthVO`。

本模块 1 个接口。**免登录**，用于确认服务已启动，
也方便接口自动化测试在开跑前做一次可用性探测（避免把"服务没起来"误判成"接口失败"）。

---

## 1. 健康检查

`GET /api/health`

| 项 | 值 |
|---|---|
| 认证 | **不需要**（白名单） |
| 成功业务码 | `200`（健康） |

### 请求参数

无。

### 请求示例

```http
GET /api/health HTTP/1.1
Host: localhost:8081
```

### 响应

**健康**（HTTP 200，`code=200`）：

```json
{
  "code": 200,
  "message": "success",
  "data": { "status": "UP", "application": "miqu", "database": "UP" }
}
```

**不健康（数据库不可用）**（HTTP 200，`code=500`）：

```json
{
  "code": 500,
  "message": "数据库不可用，服务暂时无法提供业务功能",
  "data": { "status": "DOWN", "application": "miqu", "database": "DOWN" }
}
```

`HealthVO` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `status` | string | 整体状态：`UP` / `DOWN` |
| `application` | string | 应用名，固定 `miqu` |
| `database` | string | 数据库连通性：`UP` / `DOWN` |

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 500 | `INTERNAL_ERROR` | `数据库不可用，服务暂时无法提供业务功能` | 取数据库连接失败或 `connection.isValid(2)` 为 false |

> **这是全项目唯一在失败时仍返回 `data` 的接口**（`Result.fail(errorCode, message, data)`）。
> 理由：整体 DOWN 时，调用方仍需要知道是哪个依赖挂了。

### 设计要点

**会实际探测数据库**，而不是只报告进程存活。取一次连接并做 `isValid(2)` 校验：

- 数据库挂掉时进程照样活着，但每个业务请求都会 500；
- 若健康检查此时还报 `UP`，就成了极具误导性的状态。

因此本接口的 `status` 才是可信的可用性信号。

### 测试关注点

- 正向：后端已启动 → HTTP 200 且 `code == 200`、`status == "UP"`、`database == "UP"`、`application == "miqu"`。
- **字段类型**：三个字段都是字符串（`"UP"` 而不是布尔 `true`）。
- **免登录**：不带 Token 也能访问（白名单）。
- 响应结构必须与上面完全一致（自动化测试在开跑前用它做前置探测，
  结构一变会导致整套 pytest 直接退出）。
- **数据库不可用时**：`code == 500`、`status == "DOWN"`、`database == "DOWN"`，
  且 **HTTP 状态仍是 200**（不要断言 HTTP 5xx）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| — | **无独立断言用例** |

> ⚠️ **覆盖现状**：`GET /api/health` 被 `tests/conftest.py::ensure_backend_running`
> 作为**会话级前置探测**使用（`code != 200` 时 `pytest.exit`），
> 但**没有任何测试函数对它做结构断言**。
> README 中提到的 `HealthControllerTest`（4 用例）属 **JUnit 侧**覆盖
> （`backend/src/test/java/com/miqu/controller/HealthControllerTest.java`），
> pytest 侧是缺口。详见 [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)。

JUnit：`HealthControllerTest`（4 用例，含数据库 UP/DOWN、响应结构、免登录可访问）。
