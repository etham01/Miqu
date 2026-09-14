"""并发场景。

这里只挑**最有价值**的少量场景：它们共同验证同一件事——

    Service 层的"先查再插"只是快速失败，
    **真正拦住重复数据的是数据库唯一键**（uk_follower_following / uk_post_user / uk_username / uk_users），
    由 `GlobalExceptionHandler` 把 DuplicateKeyException 翻译成 409。

所以断言的重点不是"某个请求返回了什么"，而是**最终数据只有一份**：
并发关注/点赞后计数只 +1，并发建会话收敛成同一条。

实现说明：`requests.Session` 不保证线程安全，因此每个线程各用一个
独立 Session 的 `ApiClient`（共享同一个 Token）；并用 Barrier 把线程压到
同一时刻起跑，否则"并发"很容易退化成串行，测不出竞态。
"""

from __future__ import annotations

import threading
from concurrent.futures import ThreadPoolExecutor

import pytest

from utils.client import ApiClient

WORKERS = 8


def _clone(client) -> ApiClient:
    """派生一个持有独立 Session、但复用同一 Token 的客户端。"""
    return ApiClient(client.base_url, token=client.token)


def _uid(client) -> str:
    response = client.get("/api/users/me")
    assert response.ok, response.message
    return response.data["id"]


def _make_mutual(a, b) -> None:
    for follower, target in ((a, b), (b, a)):
        response = follower.post(f"/api/users/{_uid(target)}/follow")
        assert response.ok or response.code == 409, response


def _race(workers: int, call):
    """让 workers 个线程尽量同时发起请求，返回全部响应。"""
    barrier = threading.Barrier(workers)

    def run(index: int):
        barrier.wait()
        return call(index)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(run, index) for index in range(workers)]
        return [future.result() for future in futures]


def _summarize(results) -> tuple[int, list[int]]:
    successes = sum(1 for item in results if item.ok)
    failures = sorted(item.code for item in results if not item.ok)
    return successes, failures


# ---------- 注册 ----------


@pytest.mark.write
def test_concurrent_duplicate_registration(base_url, unique_suffix):
    """同一用户名并发注册：只能成功一次，其余 409。

    这是 uk_username 唯一键的兜底验证——没有它就会出现两个同名账号。
    """
    username = f"race{unique_suffix}"

    results = _race(WORKERS, lambda _: ApiClient(base_url).register(username))

    successes, failures = _summarize(results)
    assert successes == 1, f"并发注册应恰好成功一次，实际 {successes}：{results}"
    assert set(failures) == {409}, f"其余请求应为 409 冲突，实际 {failures}"


# ---------- 关注 ----------


@pytest.mark.write
def test_concurrent_duplicate_follow(fresh_users):
    """同一用户并发关注同一目标：恰好写入 1 行，粉丝数只 +1。"""
    follower, target = fresh_users(2)
    target_id = _uid(target)

    results = _race(
        WORKERS, lambda _: _clone(follower).post(f"/api/users/{target_id}/follow")
    )

    successes, failures = _summarize(results)
    assert successes == 1, f"并发关注应恰好成功一次，实际 {successes}：{results}"
    assert set(failures) == {409}, f"其余请求应为 409 已关注，实际 {failures}"

    # 冗余计数与实际关系表必须一致
    assert target.get("/api/users/me").data["followerCount"] == 1
    assert follower.get("/api/users/me").data["followingCount"] == 1


@pytest.mark.write
def test_concurrent_unfollow(fresh_users):
    """并发取消关注：恰好一次成功，其余 404（已取消）。"""
    follower, target = fresh_users(2)
    target_id = _uid(target)
    follower.post(f"/api/users/{target_id}/follow")

    results = _race(
        WORKERS, lambda _: _clone(follower).delete(f"/api/users/{target_id}/follow")
    )

    successes, failures = _summarize(results)
    assert successes == 1, f"并发取关应恰好成功一次，实际 {successes}：{results}"
    assert set(failures) == {404}, f"其余请求应为 404 尚未关注，实际 {failures}"

    assert target.get("/api/users/me").data["followerCount"] == 0


# ---------- 点赞 ----------


@pytest.mark.write
def test_concurrent_duplicate_like(fresh_users):
    """同一用户并发点赞同一条动态：恰好 1 条点赞记录，likeCount 只 +1。"""
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "并发点赞目标"}).data["id"]

    results = _race(WORKERS, lambda _: _clone(liker).post(f"/api/posts/{post_id}/like"))

    successes, failures = _summarize(results)
    assert successes == 1, f"并发点赞应恰好成功一次，实际 {successes}：{results}"
    assert set(failures) == {409}, f"其余请求应为 409 已点赞，实际 {failures}"

    detail = author.get(f"/api/posts/{post_id}")
    assert detail.data["likeCount"] == 1


# ---------- 会话 ----------


@pytest.mark.write
def test_concurrent_create_conversation(fresh_users):
    """并发建会话必须收敛成同一条（uk_users 唯一键 + 捕获冲突后重查）。"""
    alice, bob = fresh_users(2)
    _make_mutual(alice, bob)
    bob_id = _uid(bob)

    results = _race(
        WORKERS,
        lambda _: _clone(alice).post("/api/conversations", json_body={"targetUserId": bob_id}),
    )

    assert all(item.ok for item in results), f"并发建会话不应报错：{results}"
    ids = {item.data["id"] for item in results}
    assert len(ids) == 1, f"并发建会话应得到同一个会话，实际 {ids}"


@pytest.mark.write
def test_concurrent_send_message(fresh_users):
    """并发发消息：全部落库，未读数与消息条数都等于成功数。"""
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)
    receiver_id = _uid(receiver)

    results = _race(
        WORKERS,
        lambda index: _clone(sender).post(
            "/api/messages", json_body={"receiverId": receiver_id, "content": f"并发第 {index} 条"}
        ),
    )

    successes, failures = _summarize(results)
    assert successes == WORKERS, f"并发发消息应全部成功，失败：{failures}"

    unread = receiver.get("/api/messages/unread-count").data["total"]
    assert unread == WORKERS, f"未读数应等于成功条数，实际 {unread}"

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    history = receiver.get(
        f"/api/conversations/{conversation_id}/messages", params={"size": 50}
    ).data
    assert len(history) == WORKERS, f"消息条数应等于成功条数，实际 {len(history)}"
    assert len({item["content"] for item in history}) == WORKERS
