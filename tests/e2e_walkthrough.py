"""Miqu 端到端真实链路验证。

不依赖 pytest，直接用 requests 打真实后端，把「注册 → 登录 → 搜索 → 关注 → 互关
→ 私聊 → 发消息 → 读历史 → 标记已读 → 取消互关 → 发送失败 → 历史仍可读
→ 重新互关 → 恢复发送」以及动态/通知/上传/管理端逐行走一遍。

用法：
    NO_PROXY=localhost,127.0.0.1 python e2e_walkthrough.py [base_url]
"""

from __future__ import annotations

import sys
import uuid

import requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8081").rstrip("/")
PASSWORD = "123456"

results: list[tuple[str, bool, str]] = []


def check(step: str, condition: bool, detail: str = "") -> None:
    results.append((step, bool(condition), detail))
    mark = "PASS" if condition else "FAIL"
    print(f"[{mark}] {step}" + (f"  ->  {detail}" if detail else ""))


def call(method: str, path: str, token: str | None = None, **kwargs):
    headers = kwargs.pop("headers", {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    response = requests.request(method, f"{BASE}{path}", headers=headers, timeout=20, **kwargs)
    try:
        return response.status_code, response.json()
    except ValueError:
        return response.status_code, {"raw": response.text[:200]}


def register_and_login(username: str) -> str:
    status, body = call(
        "POST",
        "/api/auth/register",
        json={
            "username": username,
            "password": PASSWORD,
            "nickname": f"演示{username[-4:]}",
            "email": f"{username}@miqu.test",
        },
    )
    assert body.get("code") == 200, f"注册失败 {body}"
    status, body = call("POST", "/api/auth/login", json={"username": username, "password": PASSWORD})
    assert body.get("code") == 200, f"登录失败 {body}"
    return body["data"]["token"]


def main() -> int:
    suffix = uuid.uuid4().hex[:8]
    user_a, user_b = f"e2e{suffix}a", f"e2e{suffix}b"

    print("=" * 72)
    print(f"Miqu 端到端验证  base={BASE}")
    print("=" * 72)

    # ---------- 健康检查 ----------
    status, body = call("GET", "/api/health")
    check("健康检查 /api/health", body.get("code") == 200 and body["data"]["database"] == "UP",
          f"db={body.get('data', {}).get('database')}")

    # ---------- 注册 / 登录 ----------
    token_a = register_and_login(user_a)
    token_b = register_and_login(user_b)
    check("注册 + 登录（两个用户）", bool(token_a and token_b), f"{user_a} / {user_b}")

    status, body = call("GET", "/api/users/me", token_a)
    id_a = body["data"]["id"]
    check("查看个人资料 /api/users/me", body.get("code") == 200, f"id={id_a}")

    # ---------- 修改资料 / 修改密码 ----------
    status, body = call("PUT", "/api/users/me", token_a, json={"nickname": "端到端测试昵称", "bio": "改资料"})
    check("修改资料 PUT /api/users/me", body.get("code") == 200 and body["data"]["nickname"] == "端到端测试昵称")

    status, body = call("PUT", "/api/users/me/password", token_a,
                        json={"oldPassword": PASSWORD, "newPassword": "654321"})
    check("修改密码 PUT /api/users/me/password", body.get("code") == 200)
    status, body = call("PUT", "/api/users/me/password", token_a,
                        json={"oldPassword": "654321", "newPassword": PASSWORD})
    check("改回原密码（保证后续步骤可用）", body.get("code") == 200)

    # ---------- 搜索 ----------
    status, body = call("GET", "/api/users/search", params={"keyword": user_b})
    found = [item["username"] for item in body.get("data", {}).get("list", [])]
    check("搜索用户 GET /api/users/search", user_b in found, f"命中={found[:3]}")

    status, body = call("GET", "/api/users/me", token_b)
    id_b = body["data"]["id"]

    # ---------- 关注 / 互关 ----------
    status, body = call("POST", f"/api/users/{id_b}/follow", token_a)
    check("关注 POST /api/users/{id}/follow", body.get("code") == 200)

    status, body = call("POST", f"/api/users/{id_b}/follow", token_a)
    check("重复关注返回 409", body.get("code") == 409, body.get("message"))

    status, body = call("GET", "/api/users/me/following", token_a, params={"size": 50})
    check("查看关注列表 /api/users/me/following", body.get("code") == 200 and body["data"]["total"] >= 1)

    status, body = call("POST", f"/api/users/{id_b}/follow", token_a)  # 占位，下面才是回关
    status, body = call("POST", f"/api/users/{id_a}/follow", token_b)
    check("B 回关 A（形成互关）", body.get("code") == 200)

    status, body = call("GET", f"/api/users/{id_b}", token_a)
    check("个人主页显示互关标识 followingMe/followedByMe",
          body["data"].get("followedByMe") is True and body["data"].get("followingMe") is True)

    # ---------- 私聊：互关可发 ----------
    status, body = call("POST", "/api/conversations", token_a, json={"targetUserId": id_b})
    check("互关后打开会话 POST /api/conversations", body.get("code") == 200, f"conversationId={body.get('data', {}).get('id')}")

    status, body = call("POST", "/api/messages", token_a, json={"receiverId": id_b, "content": "互关后第一条消息"})
    check("发送私信 POST /api/messages", body.get("code") == 200)

    status, body = call("GET", "/api/conversations", token_b)
    conversation_id = body["data"]["list"][0]["id"]
    check("B 的会话列表含该会话", body["data"]["total"] >= 1, f"id={conversation_id}")

    status, body = call("GET", f"/api/conversations/{conversation_id}/messages", token_b)
    contents = [item["content"] for item in body["data"]]
    check("读取历史消息（正序）", contents[-1:] == ["互关后第一条消息"], f"{contents}")

    status, body = call("GET", "/api/messages/unread-count", token_b)
    unread_before = body["data"]["total"]
    check("B 有未读私信", unread_before >= 1, f"unread={unread_before}")

    status, body = call("PUT", f"/api/conversations/{conversation_id}/read", token_b)
    status, body = call("GET", "/api/messages/unread-count", token_b)
    check("标记已读后未读清零", body["data"]["total"] == 0)

    # ---------- 动态 / 点赞 / 评论 ----------
    status, body = call("POST", "/api/posts", token_a, json={"content": "端到端验证发布的动态"})
    post_id = body["data"]["id"]
    check("创建动态 POST /api/posts", body.get("code") == 200, f"postId={post_id}")

    status, body = call("GET", "/api/posts", params={"size": 5})
    check("查看动态列表 GET /api/posts", body.get("code") == 200)

    status, body = call("POST", f"/api/posts/{post_id}/like", token_b)
    check("点赞 POST /api/posts/{id}/like", body.get("code") == 200)
    status, body = call("POST", f"/api/posts/{post_id}/like", token_b)
    check("重复点赞返回 409", body.get("code") == 409)

    status, body = call("POST", f"/api/posts/{post_id}/comments", token_b, json={"content": "端到端评论"})
    check("发表评论", body.get("code") == 200)

    status, body = call("GET", f"/api/posts/{post_id}")
    check("动态详情点赞/评论计数正确",
          body["data"]["likeCount"] == 1 and body["data"]["commentCount"] == 1,
          f"like={body['data']['likeCount']} comment={body['data']['commentCount']}")

    # ---------- 通知 ----------
    # 类型是数字编码：1=关注 2=点赞 3=评论（4=私信为保留值，一期不产生）
    status, body = call("GET", "/api/notifications", token_a, params={"size": 20})
    types = {item["type"] for item in body.get("data", {}).get("list", [])}
    check("通知列表覆盖关注/点赞/评论（1/2/3）",
          body.get("code") == 200 and {1, 2, 3}.issubset(types), f"types={sorted(types)}")

    status, body = call("GET", "/api/notifications/unread-count", token_a)
    unread_notify = body["data"]["total"]
    check("通知未读数 > 0", unread_notify > 0, f"unread={unread_notify}")

    status, body = call("PUT", "/api/notifications/read-all", token_a)
    status, body = call("GET", "/api/notifications/unread-count", token_a)
    check("全部标记已读后未读数清零", body.get("code") == 200 and body["data"]["total"] == 0)

    # ---------- 文件上传 ----------
    png = b"\x89PNG\r\n\x1a\n" + b"\x00" * 32
    status, body = call("POST", "/api/files/image", token_a,
                        files={"file": ("demo.png", png, "image/png")})
    url = body.get("data", {}).get("url", "")
    check("上传图片 POST /api/files/image", body.get("code") == 200, url)

    status, body = call("POST", "/api/files/image", token_a,
                        files={"file": ("shell.png", b"not an image at all, really", "image/png")})
    check("伪装图片被拒（魔数校验）", body.get("code") == 400, body.get("message"))

    if url:
        raw = requests.get(f"{BASE}{url}", timeout=20)
        check("上传后的 URL 可访问 /uploads/**", raw.status_code == 200 and "image/" in raw.headers.get("Content-Type", ""),
              f"http={raw.status_code} ct={raw.headers.get('Content-Type')}")

    # ---------- 取消互关：发送失败但历史可读 ----------
    call("DELETE", f"/api/users/{id_b}/follow", token_a)
    call("DELETE", f"/api/users/{id_a}/follow", token_b)
    status, body = call("GET", f"/api/users/{id_b}", token_a)
    check("取消互关后 followedByMe=false", body["data"].get("followedByMe") is False)

    status, body = call("POST", "/api/messages", token_a, json={"receiverId": id_b, "content": "取消互关后不该成功"})
    check("取消互关后发送私信被拒 403", body.get("code") == 403, body.get("message"))

    status, body = call("POST", "/api/conversations", token_a, json={"targetUserId": id_b})
    check("取消互关后不能重新打开会话 403", body.get("code") == 403, body.get("message"))

    status, body = call("GET", f"/api/conversations/{conversation_id}/messages", token_b)
    check("取消互关后历史消息仍可读取", body.get("code") == 200 and len(body["data"]) >= 1)

    status, body = call("PUT", f"/api/conversations/{conversation_id}/read", token_b)
    check("取消互关后仍可标记已读", body.get("code") == 200)

    # ---------- 重新互关：恢复发送 ----------
    call("POST", f"/api/users/{id_b}/follow", token_a)
    call("POST", f"/api/users/{id_a}/follow", token_b)
    status, body = call("POST", "/api/messages", token_a, json={"receiverId": id_b, "content": "重新互关后恢复发送"})
    check("重新互关后恢复发送", body.get("code") == 200)

    # ---------- 管理员 ----------
    status, body = call("POST", "/api/auth/login", json={"username": "admin", "password": PASSWORD})
    admin_token = body["data"]["token"]
    check("管理员登录", body.get("code") == 200)

    status, body = call("GET", "/api/admin/stats", admin_token)
    check("管理端统计 /api/admin/stats", body.get("code") == 200)

    status, body = call("GET", "/api/admin/users", admin_token, params={"size": 5})
    check("管理端用户管理 /api/admin/users", body.get("code") == 200)

    status, body = call("GET", "/api/admin/reports", admin_token, params={"size": 5})
    check("管理端举报处理 /api/admin/reports", body.get("code") == 200)

    status, body = call("GET", "/api/admin/stats")
    check("未登录访问管理端返回 401", body.get("code") == 401)

    # ---------- 汇总 ----------
    passed = sum(1 for _, ok, _ in results if ok)
    print("=" * 72)
    print(f"结果：{passed}/{len(results)} 通过")
    failed = [name for name, ok, _ in results if not ok]
    if failed:
        print("失败步骤：")
        for name in failed:
            print(f"  - {name}")
    print("=" * 72)
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
