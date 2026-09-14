# 文件 API

> 通用约定见 [API_CONVENTIONS.md](./API_CONVENTIONS.md)。
> 事实来源：`FileController` / `LocalFileStorageService` / `FileUploadVO`。

本模块 1 个接口。只负责"存文件 + 返回 URL"，
头像（`PUT /api/users/me/avatar`）与动态图片（`POST /api/posts`）都复用此接口，
业务接口只接收 URL，上传与业务解耦，两边都好测。

---

## 1. 上传图片

`POST /api/files/image`

| 项 | 值 |
|---|---|
| 认证 | **需要**（`/api/files/**` 刻意不在白名单） |
| Content-Type | `multipart/form-data` |
| 成功业务码 | `200` |

### 请求参数（form-data）

| 参数 | 类型 | 必填 | 示例 | 说明 |
|---|---|---|---|---|
| `file` | file | ✅ | `photo.png` | 表单字段名固定为 **`file`**；大小上限 **5MB** |

### 请求示例

```bash
curl -X POST http://localhost:8081/api/files/image \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -F "file=@photo.png"
```

### 响应

`data` 为 `FileUploadVO`：

```json
{
  "code": 200,
  "message": "success",
  "data": { "url": "/uploads/image/2026/09/3f2a9c1d8e4b4a7f9c0d1e2f3a4b5c6d.png" }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `url` | string | 可直接用于 `<img src>` 的**相对路径**；拼接 Base URL 后可访问 |

**访问方式**：`GET /uploads/image/2026/09/<uuid>.png`（静态资源映射，**免登录**）。

### 错误情况

| code | ErrorCode | message | 触发条件 |
|---|---|---|---|
| 400 | `FILE_EMPTY` | `上传文件不能为空` | 未选择文件 / 文件为空（`file.isEmpty()`） |
| 400 | `FILE_TOO_LARGE` | `图片大小不能超过 5MB` | **两处**：Service 的 `size > 5MB` 检查，或 Servlet 的 `max-file-size` 超限（`MaxUploadSizeExceededException`） |
| 400 | `FILE_TYPE_NOT_ALLOWED` | `仅支持 jpg / png / gif / webp 格式的图片` | **文件头魔数**不属于 jpg/png/gif/webp |
| 400 | `PARAM_INVALID` | `缺少必要参数：file` | 完全没传 `file` 字段 |
| 401 | `UNAUTHORIZED` | `未登录或登录状态已失效` | 无 Token |
| 500 | `INTERNAL_ERROR` | `系统异常，请稍后重试` / `读取上传文件失败` / `保存文件失败` | IO 异常；路径越界兜底 |

### 安全实现要点（测试重点）

1. **按文件头魔数判类型**，不看后缀名——把脚本改成 `.jpg` 也会被拒。识别规则：

| 格式 | 魔数（字节） | 返回扩展名 |
|---|---|---|
| JPEG | `FF D8 FF` | `jpg` |
| PNG | `89 50 4E 47 0D 0A 1A 0A` | `png` |
| GIF | `47 49 46 38`（`GIF8`） | `gif` |
| WebP | `52 49 46 46`（`RIFF`）… 偏移 8 起 `57 45 42 50`（`WEBP`） | `webp` |

2. **用 UUID 重命名**（`uuid去掉横线 + 扩展名`），**不使用用户原始文件名**，杜绝 `../../` 路径穿越。
3. 按 `image/yyyy/MM` 分目录存放，避免单目录文件过多。
4. 目标目录必须仍位于上传根目录之下（`targetDir.startsWith(baseDir)`），否则 500 兜底拒绝。
5. 上传目录为 `${UPLOAD_DIR:./uploads}` 的**绝对路径**。

> ⚠️ **已知边界**：魔数识别前有 `if (bytes.length < 12) return null;`，
> 因此**小于 12 字节的文件一律被拒为"格式不支持"**——
> 即使它的前几个字节是合法 PNG/GIF 魔数。这是极小的合法图片会被拒的原因，
> 属实现现状（是否预期需产品确认），已登记在审计报告中。

### 测试关注点

- 正向：上传合法 png/jpg/gif/webp → 200，返回 `/uploads/...` URL，
  且该 URL **能被 `GET` 到**（验证静态资源映射）。
- **空文件** → 400 `上传文件不能为空`。
- **超过 5MB** → 400 `图片大小不能超过 5MB`。
- **伪后缀**：把文本文件改名成 `.png` 上传 → 400 `仅支持 jpg / png / gif / webp 格式的图片`（魔数不符）。
- **极小文件**（<12 字节）→ 400 格式不支持（见上方已知边界）。
- **未登录** → 401（该接口不在白名单，否则任何人都能往服务器写文件）。
- 文件名安全：响应中的文件名是 UUID，**不含原始文件名**。
- 上传后把 URL 提交给 `PUT /api/users/me/avatar` 或 `POST /api/posts` 应成功
  （后者要求 URL 以 `/uploads/` 开头，本接口返回值天然满足）。

### 对应自动化测试

| 测试函数 | 覆盖点 |
|---|---|
| — | **当前无 pytest 用例** |

> ⚠️ **覆盖缺口**：`README.md` 与 `tests/README.md` 都记录了
> `tests/api/test_file.py`（9 用例：正常上传 / 空文件 / 超 5MB / 伪后缀 / 极小文件 / 未登录），
> 但**该文件当前不在仓库中**，`pytest --collect-only` 也收集不到。
> 因此**本接口目前没有任何自动化测试覆盖**（JUnit 侧同样没有对应的上传测试类）。
> 详见 [docs/testing/API_DOCUMENT_AUDIT.md](../testing/API_DOCUMENT_AUDIT.md)。

JUnit：无。
