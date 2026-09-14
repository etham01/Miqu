"""BUG-001 复现探针（只读分析用，不改任何代码）。

验证：取消关注后，对方的动态是否仍出现在「关注」动态流。

分两层定位：
  A) API 层：follow → post → following feed → unfollow → following feed
  B) 数据库层：直接查 follow 表，确认关系是否真的被物理删除

用法：python bug001_probe.py [base_url]
"""

from __future__ import annotations

import sys
import uuid

import requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8081").rstrip("/")
PW = "123456"


def call(method, path, token=None, **kw):
    headers = kw.pop("headers", {})
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.request(method, f"{BASE}{path}", headers=headers, timeout=20, **kw)
    try:
        return r.status_code, r.json()
    except ValueError:
        return r.status_code, {"raw": r.text[:200]}


def new_user(prefix):
    name = f"{prefix}{uuid.uuid4().hex[:8]}"
    s, b = call("POST", "/api/auth/register", json={
        "username": name, "password": PW,
        "nickname": f"探针{name[-4:]}", "email": f"{name}@miqu.test"})
    assert b.get("code") == 200, b
    s, b = call("POST", "/api/auth/login", json={"username": name, "password": PW})
    assert b.get("code") == 200, b
    return name, b["data"]["token"], str(b["data"]["user"]["id"])


def feed_author_ids(token):
    s, b = call("GET", "/api/posts", token=token, params={"tab": "following", "size": 50})
    assert b.get("code") == 200, b
    return [i["author"]["id"] for i in b["data"]["list"]], b["data"]["total"]


def main():
    print("=" * 70)
    print("BUG-001 复现探针")
    print("=" * 70)

    a_name, a_token, a_id = new_user("buga")
    b_name, b_token, b_id = new_user("bugb")
    print(f"A = {a_name} (id={a_id})")
    print(f"B = {b_name} (id={b_id})")

    # --- 初始：A 未关注 B ---
    ids, total = feed_author_ids(a_token)
    print(f"\n[1] 关注前 A 的关注流作者: {ids} (total={total})")
    print(f"    B 是否出现: {b_id in ids}")

    # --- A 关注 B ---
    s, b = call("POST", f"/api/users/{b_id}/follow", token=a_token)
    print(f"\n[2] A 关注 B -> code={b.get('code')}")

    # --- B 发动态 ---
    s, b = call("POST", "/api/posts", token=b_token, json={"content": f"BUG-001 探针动态 by {b_name}"})
    post_id = b["data"]["id"]
    print(f"[3] B 发布动态 -> postId={post_id} code={b.get('code')}")

    ids, total = feed_author_ids(a_token)
    print(f"[4] A 的关注流作者: {ids} (total={total})")
    print(f"    B 是否出现（期望 True）: {b_id in ids}   {'✓' if b_id in ids else '✗ 异常'}")

    # --- A 取消关注 B ---
    s, b = call("DELETE", f"/api/users/{b_id}/follow", token=a_token)
    print(f"\n[5] A 取消关注 B -> code={b.get('code')} message={b.get('message')}")

    # --- 直接看 follow 表（由调用方另行用 mysql 验证）---
    s, b = call("GET", f"/api/users/{b_id}", token=a_token)
    print(f"[6] B 主页 followedByMe（期望 False）: {b['data'].get('followedByMe')}  "
          f"{'✓' if b['data'].get('followedByMe') is False else '✗ 异常'}")
    print(f"    B 主页 followerCount（关注期间应为 1，取消后 0）: {b['data'].get('followerCount')}")

    # --- 关键断言：取消关注后的关注流 ---
    ids, total = feed_author_ids(a_token)
    print(f"\n[7] 取消关注后 A 的关注流作者: {ids} (total={total})")
    still = b_id in ids
    print(f"    B 是否仍出现（期望 False）: {still}   {'✗ BUG 复现' if still else '✓ 正常'}")

    # --- 再查一次（排除偶发）---
    ids2, total2 = feed_author_ids(a_token)
    print(f"[8] 再查一次: 作者={ids2} total={total2}  B 出现={b_id in ids2}")

    # --- 分页验证：size=1 翻页 ---
    s, b = call("GET", "/api/posts", token=a_token, params={"tab": "following", "size": 1})
    print(f"[9] size=1 第一页: total={b['data']['total']} 列表长度={len(b['data']['list'])}")

    print("\n" + "=" * 70)
    print(f"结论: 取消关注后 B 的动态{'仍然出现 -> BUG-001 复现' if still else '已消失 -> 后端行为正确'}")
    print("=" * 70)
    print(f"\n供数据库核对: A_id={a_id} B_id={b_id} post_id={post_id}")
    return 0 if not still else 1


if __name__ == "__main__":
    raise SystemExit(main())
