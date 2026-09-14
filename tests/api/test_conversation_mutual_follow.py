"""互关私聊规则 —— 会话打开侧（2026-09-11 冻结）。

与 `test_message_mutual_follow.py` 是同一规则的两个入口：

    `POST /api/conversations`（显式打开会话）与 `POST /api/messages`（发消息时隐式建会话）
    都要求互关，都返回 403 `NOT_MUTUAL_FOLLOW`。

区别在于**限制的边界**：打开会话这条路径被拦住，不代表历史会话读不了。
`listMessages` / `markRead` 走的是 `requireMember`（只校验参与者身份），
不经过互关校验——这是有意设计，不是遗漏。
"""

from __future__ import annotations

import pytest

NOT_MUTUAL_MESSAGE = "需要互相关注后才能私聊"
SEED_BANNED_ID = 12  # banned001，状态禁用
UNKNOWN_USER_ID = "99999999"


def _uid(client) -> str:
    response = client.get("/api/users/me")
    assert response.ok, response.message
    return response.data["id"]


def _make_mutual(a, b) -> None:
    for follower, target in ((a, b), (b, a)):
        response = follower.post(f"/api/users/{_uid(target)}/follow")
        assert response.ok or response.code == 409, response


def _break_mutual(a, b) -> None:
    for follower, target in ((a, b), (b, a)):
        response = follower.delete(f"/api/users/{_uid(target)}/follow")
        assert response.ok or response.code == 404, response


def _open(client, target_id):
    return client.post("/api/conversations", json_body={"targetUserId": target_id})


def _send(sender, receiver_id, content="你好"):
    return sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": content})


# ---------- 允许打开 ----------


@pytest.mark.smoke
@pytest.mark.write
def test_mutual_follow_allows_opening(fresh_users):
    alice, bob = fresh_users(2)
    _make_mutual(alice, bob)

    response = _open(alice, _uid(bob))

    assert response.ok, response.message
    assert response.data["partner"]["id"] == _uid(bob)


# ---------- 拒绝打开 ----------


@pytest.mark.smoke
@pytest.mark.write
def test_stranger_cannot_open_conversation(fresh_users):
    alice, stranger = fresh_users(2)

    response = _open(alice, _uid(stranger))

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_one_way_follow_cannot_open_conversation(fresh_users):
    alice, bob = fresh_users(2)
    alice.post(f"/api/users/{_uid(bob)}/follow")

    response = _open(alice, _uid(bob))

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_cannot_reopen_after_unfollow(fresh_users):
    """解除互关后，即使会话已经存在也不能再打开。"""
    alice, bob = fresh_users(2)
    _make_mutual(alice, bob)
    bob_id = _uid(bob)
    assert _open(alice, bob_id).ok

    _break_mutual(alice, bob)
    response = _open(alice, bob_id)

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


# ---------- 限制不影响历史会话 ----------


@pytest.mark.write
def test_existing_conversation_stays_readable_after_unfollow(fresh_users):
    """存量会话：解除互关后仍可读取历史消息（可读不可发）。"""
    alice, bob = fresh_users(2)
    _make_mutual(alice, bob)
    _send(alice, _uid(bob), "解除互关前的一句")

    conversation_id = bob.get("/api/conversations").data["list"][0]["id"]
    _break_mutual(alice, bob)

    history = bob.get(f"/api/conversations/{conversation_id}/messages")

    assert history.ok, history.message
    assert [item["content"] for item in history.data] == ["解除互关前的一句"]


# ---------- 参与者身份校验 ----------


@pytest.mark.write
def test_non_member_cannot_read_conversation(fresh_users):
    """非参与者读会话 → 403 `NOT_CONVERSATION_MEMBER`。

    这与互关校验是两回事：即使陌生人之间互关了，也读不到别人的会话。
    """
    alice, bob, stranger = fresh_users(3)
    _make_mutual(alice, bob)
    _send(alice, _uid(bob), "两个人的私密对话")

    conversation_id = alice.get("/api/conversations").data["list"][0]["id"]
    response = stranger.get(f"/api/conversations/{conversation_id}/messages")

    assert response.code == 403
    assert response.message == "你不是该会话的参与者"


@pytest.mark.write
def test_non_member_cannot_mark_read(fresh_users):
    alice, bob, stranger = fresh_users(3)
    _make_mutual(alice, bob)
    _send(alice, _uid(bob), "旁观者不能标记已读")

    conversation_id = alice.get("/api/conversations").data["list"][0]["id"]
    response = stranger.put(f"/api/conversations/{conversation_id}/read")

    assert response.code == 403
    assert response.message == "你不是该会话的参与者"


# ---------- 校验顺序 ----------


@pytest.mark.write
def test_open_self_precedes_mutual_check(fresh_user):
    """与自己发起会话 → 400，优先级高于互关校验。"""
    client = fresh_user()

    response = _open(client, _uid(client))

    assert response.code == 400
    assert response.message == "不能给自己发送私信"


@pytest.mark.write
def test_open_unknown_user_precedes_mutual_check(fresh_user):
    """目标不存在 → 404，优先级高于互关校验。"""
    client = fresh_user()

    response = _open(client, UNKNOWN_USER_ID)

    assert response.code == 404
    assert response.message == "用户不存在"


@pytest.mark.write
def test_open_disabled_user_precedes_mutual_check(fresh_user):
    """目标被禁用 → 423，优先级高于互关校验。"""
    client = fresh_user()

    response = _open(client, SEED_BANNED_ID)

    assert response.code == 423
    assert response.message == "账号已被禁用，请联系管理员"


def test_open_conversation_requires_login(anonymous):
    assert _open(anonymous, 3).code == 401
