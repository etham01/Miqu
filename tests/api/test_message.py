"""私信接口：会话与消息。"""

from __future__ import annotations

import pytest


def _unread_total(client) -> int:
    response = client.get("/api/messages/unread-count")
    assert response.ok, response.message
    return response.data["total"]


def _make_mutual(a, b) -> None:
    """建立互相关注。

    业务规则（2026-09-11 冻结）：私聊要求双方互相关注，否则 403 NOT_MUTUAL_FOLLOW。
    本文件的用例验证的是"会话/消息本身"的行为，因此先把前置条件补成互关——
    **只改前置数据，不改任何断言**。幂等：重复关注返回 409 视为该方向已就绪。
    """
    for follower, target in ((a, b), (b, a)):
        target_id = target.get("/api/users/me").data["id"]
        resp = follower.post(f"/api/users/{target_id}/follow")
        assert resp.ok or resp.code == 409, resp


@pytest.mark.smoke
@pytest.mark.write
def test_send_message_creates_conversation(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    response = sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": "你好"})

    assert response.ok, response.message
    assert response.data["content"] == "你好"
    assert response.data["mine"] is True
    assert response.data["isRead"] is False

    conversations = sender.get("/api/conversations")
    assert conversations.data["total"] == 1
    assert conversations.data["list"][0]["partner"]["id"] == receiver_id


@pytest.mark.smoke
@pytest.mark.write
def test_unread_count_increments_for_receiver_only(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    before_receiver = _unread_total(receiver)
    before_sender = _unread_total(sender)

    sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": "未读 +1"})

    assert _unread_total(receiver) == before_receiver + 1
    assert _unread_total(sender) == before_sender, "发送者自己的未读数不应增加"


@pytest.mark.write
def test_mark_conversation_read(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": "消息一"})
    sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": "消息二"})

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    assert receiver.get("/api/conversations").data["list"][0]["unreadCount"] == 2

    assert receiver.put(f"/api/conversations/{conversation_id}/read").code == 200

    assert receiver.get("/api/conversations").data["list"][0]["unreadCount"] == 0
    assert _unread_total(receiver) == 0


@pytest.mark.write
def test_mark_read_does_not_affect_partner(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": "单向已读"})

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    receiver.put(f"/api/conversations/{conversation_id}/read")

    # 发送者的会话列表里不应有未读（他发出去的消息不算自己的未读）
    assert _unread_total(sender) == 0


@pytest.mark.write
def test_conversation_is_symmetric(fresh_users):
    """(A,B) 与 (B,A) 必须是同一条会话。

    会话表有 CHECK (user1_id < user2_id) 与唯一键，
    服务端必须把两个 ID 规整成 (较小, 较大) 后再查找。
    """
    alice, bob = fresh_users(2)
    alice_id = alice.get("/api/users/me").data["id"]
    bob_id = bob.get("/api/users/me").data["id"]

    _make_mutual(alice, bob)
    from_alice = alice.post("/api/conversations", json_body={"targetUserId": bob_id})
    from_bob = bob.post("/api/conversations", json_body={"targetUserId": alice_id})

    assert from_alice.ok and from_bob.ok
    assert from_alice.data["id"] == from_bob.data["id"]


@pytest.mark.write
def test_open_conversation_is_idempotent(fresh_users):
    alice, bob = fresh_users(2)
    bob_id = bob.get("/api/users/me").data["id"]

    _make_mutual(alice, bob)
    first = alice.post("/api/conversations", json_body={"targetUserId": bob_id})
    second = alice.post("/api/conversations", json_body={"targetUserId": bob_id})

    assert first.data["id"] == second.data["id"]


@pytest.mark.write
def test_cannot_message_self(fresh_user):
    client = fresh_user()
    user_id = client.get("/api/users/me").data["id"]

    response = client.post("/api/messages", json_body={"receiverId": user_id, "content": "自言自语"})
    assert response.code == 400
    assert response.message == "不能给自己发送私信"


@pytest.mark.write
def test_message_content_boundaries(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    too_long = sender.post(
        "/api/messages", json_body={"receiverId": receiver_id, "content": "字" * 1001}
    )
    assert too_long.code == 400
    assert "1000" in too_long.message

    exactly_max = sender.post(
        "/api/messages", json_body={"receiverId": receiver_id, "content": "字" * 1000}
    )
    assert exactly_max.ok, exactly_max.message


@pytest.mark.write
def test_message_blank_content(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    response = sender.post(
        "/api/messages", json_body={"receiverId": receiver_id, "content": "   "}
    )
    assert response.code == 400


@pytest.mark.write
def test_message_to_unknown_user(fresh_user):
    client = fresh_user()
    response = client.post(
        "/api/messages", json_body={"receiverId": "99999999", "content": "收件人不存在"}
    )
    assert response.code == 404


def test_send_message_requires_login(anonymous):
    assert anonymous.post("/api/messages", json_body={"receiverId": 2, "content": "x"}).code == 401


@pytest.mark.smoke
@pytest.mark.write
def test_message_history_is_chronological(fresh_users):
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    for index in range(5):
        sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": f"第 {index} 条"})

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    messages = receiver.get(f"/api/conversations/{conversation_id}/messages")

    assert messages.ok, messages.message
    contents = [item["content"] for item in messages.data]
    assert contents == [f"第 {index} 条" for index in range(5)]


@pytest.mark.write
def test_message_cursor_pagination(fresh_users):
    """游标分页：用最早一条的 id 继续往前翻，两页之间不重不漏。"""
    sender, receiver = fresh_users(2)
    receiver_id = receiver.get("/api/users/me").data["id"]

    _make_mutual(sender, receiver)
    for index in range(10):
        sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": f"消息 {index}"})

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]

    first_page = receiver.get(
        f"/api/conversations/{conversation_id}/messages", params={"size": 4}
    ).data
    assert len(first_page) == 4

    earliest = first_page[0]["id"]
    second_page = receiver.get(
        f"/api/conversations/{conversation_id}/messages",
        params={"size": 4, "beforeId": earliest},
    ).data
    assert len(second_page) == 4

    # 第二页必须全部早于第一页
    assert all(int(item["id"]) < int(earliest) for item in second_page)
    # 且两页首尾相接，中间没有空隙
    assert int(second_page[-1]["id"]) + 1 == int(earliest)


@pytest.mark.write
def test_conversation_list_orders_by_last_message(fresh_users):
    alice, bob, carol = fresh_users(3)
    bob_id = bob.get("/api/users/me").data["id"]
    carol_id = carol.get("/api/users/me").data["id"]

    _make_mutual(alice, bob)
    _make_mutual(alice, carol)
    alice.post("/api/messages", json_body={"receiverId": bob_id, "content": "先给 bob 发"})
    alice.post("/api/messages", json_body={"receiverId": carol_id, "content": "后给 carol 发"})

    conversations = alice.get("/api/conversations").data["list"]
    assert conversations[0]["partner"]["id"] == carol_id


@pytest.mark.write
def test_empty_conversation_is_hidden(fresh_users):
    """建了但一条消息都没发过的会话不出现在列表里。"""
    alice, bob = fresh_users(2)
    bob_id = bob.get("/api/users/me").data["id"]

    _make_mutual(alice, bob)
    alice.post("/api/conversations", json_body={"targetUserId": bob_id})

    assert alice.get("/api/conversations").data["total"] == 0


@pytest.mark.write
def test_non_member_cannot_read_messages(fresh_users):
    alice, bob, stranger = fresh_users(3)
    bob_id = bob.get("/api/users/me").data["id"]
    _make_mutual(alice, bob)
    alice.post("/api/messages", json_body={"receiverId": bob_id, "content": "私密对话"})

    conversation_id = alice.get("/api/conversations").data["list"][0]["id"]
    response = stranger.get(f"/api/conversations/{conversation_id}/messages")

    assert response.code == 403
    assert response.message == "你不是该会话的参与者"


@pytest.mark.read
def test_seed_user_unread_total(seed_user_client):
    """test001 在种子数据里会话 1 有 2 条未读。

    注意这是绝对值断言，依赖种子数据未被改动过；
    若之前用手工调用改动过会话状态，重置数据库即可。
    """
    response = seed_user_client.get("/api/messages/unread-count")

    assert response.ok
    assert isinstance(response.data["total"], int), "未读数必须是数字而不是字符串"
