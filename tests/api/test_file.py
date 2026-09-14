"""文件上传接口（`POST /api/files/image`）——安全边界。

这是审计报告里点名的**零自动化覆盖接口**（`docs/testing/API_DOCUMENT_AUDIT.md`）。
服务端的安全策略是「只看文件头魔数，不看后缀名与 Content-Type」，
所以这里的用例重点是：**把能绕过后缀校验的攻击方式逐个打一遍**。

实现见 `LocalFileStorageService.detectImageExtension()`：
魔数不匹配 → 400 `FILE_TYPE_NOT_ALLOWED`；长度不足 12 字节也走同一条分支。
"""

from __future__ import annotations

import pytest

# 构造最小可识别的图片字节：前 12 字节是魔数，后面补零凑长度。
# 注意 detectImageExtension 要求 length >= 12，所以这些样本都必须 >= 12 字节。
PNG_BYTES = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16
JPEG_BYTES = b"\xff\xd8\xff\xe0" + b"\x00" * 16
GIF_BYTES = b"GIF89a" + b"\x00" * 16
WEBP_BYTES = b"RIFF" + b"\x00\x00\x00\x00" + b"WEBP" + b"\x00" * 8

# 纯文本伪装成图片：改后缀、改 Content-Type 都骗不过魔数校验
FAKE_IMAGE_BYTES = b"<?php echo 'not an image at all'; ?>"
# 小于 12 字节：长度检查直接拦掉
TINY_BYTES = b"ab"

MAX_SIZE = 5 * 1024 * 1024


def _upload(client, content: bytes, filename: str = "a.png", content_type: str = "image/png"):
    return client.post(
        "/api/files/image",
        files={"file": (filename, content, content_type)},
    )


# ---------- 正常路径 ----------


@pytest.mark.smoke
@pytest.mark.write
@pytest.mark.parametrize(
    "content,filename,content_type",
    [
        (PNG_BYTES, "a.png", "image/png"),
        (JPEG_BYTES, "a.jpg", "image/jpeg"),
        (GIF_BYTES, "a.gif", "image/gif"),
        (WEBP_BYTES, "a.webp", "image/webp"),
    ],
)
def test_upload_supported_image_types(fresh_user, content, filename, content_type):
    """四种受支持格式都能上传，返回 `/uploads/` 下的可访问 URL。"""
    client = fresh_user()
    response = _upload(client, content, filename, content_type)

    assert response.ok, response.message
    url = response.data["url"]
    assert url.startswith("/uploads/")

    # 存储时用 UUID 重命名：文件名是 32 位十六进制，扩展名按真实内容推导，
    # 绝不使用用户提供的原始文件名（否则会带来路径穿越与覆盖风险）
    stored_name = url.rsplit("/", 1)[-1]
    stem, _, extension = stored_name.rpartition(".")
    assert stored_name != filename
    assert len(stem) == 32 and all(c in "0123456789abcdef" for c in stem)
    assert extension == filename.rpartition(".")[2].lower()


@pytest.mark.write
def test_uploaded_file_is_reachable(fresh_user):
    """上传后返回的 URL 必须真的能取到内容（upload → 校验 → 访问 闭环）。"""
    client = fresh_user()
    url = _upload(client, PNG_BYTES).data["url"]

    fetched = client.get(url)

    assert fetched.http_status == 200
    assert "image/" in fetched.headers.get("Content-Type", "")


# ---------- 安全边界 ----------


@pytest.mark.write
def test_empty_file_is_rejected(fresh_user):
    """空文件 → 400 `FILE_EMPTY`。"""
    client = fresh_user()
    response = _upload(client, b"", "empty.png")

    assert response.code == 400
    assert response.message == "上传文件不能为空"


@pytest.mark.write
def test_oversize_file_is_rejected(fresh_user):
    """超过 5MB → 400 `FILE_TOO_LARGE`。

    可能由两处拦下：Spring 的 multipart 上限（MaxUploadSizeExceededException）
    或 Service 层的显式判断，两者都映射到同一个错误码。
    """
    client = fresh_user()
    response = _upload(client, PNG_BYTES + b"\x00" * (MAX_SIZE + 1), "huge.png")

    assert response.code == 400
    assert response.message == "图片大小不能超过 5MB"


@pytest.mark.write
def test_disguised_extension_is_rejected(fresh_user):
    """把脚本改名为 .jpg 提交 → 后缀骗不过魔数校验。"""
    client = fresh_user()
    response = _upload(client, FAKE_IMAGE_BYTES, "shell.jpg", "image/jpeg")

    assert response.code == 400
    assert response.message == "仅支持 jpg / png / gif / webp 格式的图片"


@pytest.mark.write
def test_mime_type_lies_about_content(fresh_user):
    """Content-Type 声明 image/png，实际内容是文本 → 依然被拒。

    这说明校验依据是**内容**而不是客户端可任意伪造的 MIME 声明。
    """
    client = fresh_user()
    response = _upload(client, FAKE_IMAGE_BYTES, "a.png", "image/png")

    assert response.code == 400
    assert response.message == "仅支持 jpg / png / gif / webp 格式的图片"


@pytest.mark.write
def test_tiny_file_is_rejected(fresh_user):
    """2 字节的极小文件 → 长度不足 12，无法判定魔数，按类型不合法拒绝。"""
    client = fresh_user()
    response = _upload(client, TINY_BYTES, "t.png")

    assert response.code == 400
    assert response.message == "仅支持 jpg / png / gif / webp 格式的图片"


@pytest.mark.write
def test_real_image_with_wrong_extension_is_accepted(fresh_user):
    """反向用例：内容是真 PNG，只是文件名/Content-Type 写错 → 应当放行。

    服务端既然按内容判定，就不该被错误的后缀名连累。
    """
    client = fresh_user()
    response = _upload(client, PNG_BYTES, "photo.txt", "text/plain")

    assert response.ok, response.message
    # 落库的扩展名按真实内容推导为 png，而不是沿用 .txt
    assert response.data["url"].endswith(".png")


# ---------- 认证 ----------


def test_upload_requires_login(anonymous):
    assert _upload(anonymous, PNG_BYTES).code == 401


def test_upload_missing_file_param(fresh_user):
    """multipart 请求里没有 `file` 字段 → 400 缺少必要参数（而不是 500）。"""
    client = fresh_user()
    response = client.post(
        "/api/files/image", files={"other": ("x.txt", b"x", "text/plain")}
    )

    assert response.code == 400
    assert "file" in response.message


def test_upload_wrong_content_type(fresh_user):
    """非 multipart 请求 → 400 不支持的请求类型（而不是 500）。

    这里**曾经是 500**：`HttpMediaTypeNotSupportedException` 没有对应的 handler，
    直接落到了 Exception 兜底分支。客户端发错 Content-Type 属于请求错误，
    必须能被接口测试区分出来，否则"服务端炸了"和"请求发错了"会长得一模一样。
    已于 2026-09-14 在 GlobalExceptionHandler 中补上映射。
    """
    client = fresh_user()
    response = client.post("/api/files/image", data={"other": "x"})

    assert response.code == 400
    assert "不支持的请求类型" in response.message
