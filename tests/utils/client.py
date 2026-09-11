"""Miqu 接口自动化测试 —— HTTP 客户端封装。

后端有一条贯穿所有接口的约定：**HTTP 状态始终是 200，业务结果放在响应体的 `code` 里**
（见 `GlobalExceptionHandler`）。所以断言业务结果要看 `code`，不能看 HTTP 状态。
本封装把这一点固化下来，避免每个用例各写一遍。
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

import requests


@dataclass
class ApiResponse:
    """一次接口调用的结果。"""

    http_status: int
    code: int
    message: str
    data: Any = None
    raw_text: str = ""
    headers: dict[str, str] = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        """业务是否成功。"""
        return self.code == 200

    @property
    def is_conflict(self) -> bool:
        return self.code == 409

    def __repr__(self) -> str:  # 失败时打印更可读
        return f"<ApiResponse http={self.http_status} code={self.code} message={self.message!r}>"


class ApiClient:
    """带 Token 的接口客户端。每个实例持有独立的 requests.Session。"""

    def __init__(self, base_url: str, token: str | None = None, timeout: int = 15) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout
        self.session = requests.Session()

    # ---------- 基础请求 ----------

    def _headers(self, extra: dict[str, str] | None = None) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if extra:
            headers.update(extra)
        return headers

    def request(
        self,
        method: str,
        path: str,
        *,
        json_body: Any = None,
        params: dict[str, Any] | None = None,
        files: dict[str, Any] | None = None,
        data: dict[str, Any] | None = None,
        headers: dict[str, str] | None = None,
    ) -> ApiResponse:
        url = f"{self.base_url}{path}"
        response = self.session.request(
            method=method.upper(),
            url=url,
            json=json_body,
            params=params,
            files=files,
            data=data,
            headers=self._headers(headers),
            timeout=self.timeout,
        )
        return self._parse(response)

    @staticmethod
    def _parse(response: requests.Response) -> ApiResponse:
        text = response.text or ""
        try:
            payload = response.json()
        except json.JSONDecodeError:
            # 后端异常时可能返回 HTML 错误页（如 Tomcat 的 400 页面），
            # 这种情况把原文带回去，便于排查而不是抛一个难懂的异常
            return ApiResponse(
                http_status=response.status_code,
                code=-1,
                message="响应不是合法 JSON",
                data=None,
                raw_text=text[:500],
                headers=dict(response.headers),
            )

        return ApiResponse(
            http_status=response.status_code,
            code=payload.get("code", -1),
            message=payload.get("message", ""),
            data=payload.get("data"),
            raw_text=text[:500],
            headers=dict(response.headers),
        )

    # ---------- 便捷方法 ----------

    def get(self, path: str, **kwargs: Any) -> ApiResponse:
        return self.request("GET", path, **kwargs)

    def post(self, path: str, **kwargs: Any) -> ApiResponse:
        return self.request("POST", path, **kwargs)

    def put(self, path: str, **kwargs: Any) -> ApiResponse:
        return self.request("PUT", path, **kwargs)

    def delete(self, path: str, **kwargs: Any) -> ApiResponse:
        return self.request("DELETE", path, **kwargs)

    # ---------- 业务快捷方式 ----------

    def login(self, username: str, password: str = "123456") -> ApiResponse:
        """登录，并把返回的 Token 记到本客户端上（返回的仍是响应，便于断言失败场景）。"""
        response = self.post("/api/auth/login", json_body={"username": username, "password": password})
        if response.ok and isinstance(response.data, dict):
            self.token = response.data.get("token")
        return response

    def register(self, username: str, password: str = "123456", **overrides: Any) -> ApiResponse:
        payload = {
            "username": username,
            "password": password,
            "nickname": overrides.pop("nickname", f"测试用户{username[-4:]}"),
            "email": overrides.pop("email", f"{username}@miqu.test"),
            **overrides,
        }
        return self.post("/api/auth/register", json_body=payload)

    def with_token(self, token: str) -> "ApiClient":
        """派生一个使用指定 Token 的客户端，共享 base_url。"""
        return ApiClient(self.base_url, token=token, timeout=self.timeout)
