"""互关私聊规则 —— 私信发送侧（2026-09-11 冻结）。

规则：

    只有**双方互相关注**才能发私信；非互关 / 单向关注 / 解除了互关 → 403 `NOT_MUTUAL_FOLLOW`。
    但**历史消息读取（listMessages）与标记已读（markRead）不受限制**——
    规则限制的是"发起"，不是"查看已经存在的对话"。

实现位置：`MessageServiceImpl.send()` 在写入 message / 创建会话**之前**校验互关，
所以被拦下的请求不会留下任何会话或消息（下面的 `test_blocked_send_leaves_no_trace` 钉死这一点）。

校验顺序（**实测**得出，别再靠猜）：

    ① DTO 上的 Bean Validation 最先跑：content 为空 → 400，超过 1000 字 → 400
    ② 进入 Service 之后：不能给自己(400) → 接收者不存在/已注销(404)、已禁用(423) → 互关(403)
    ③ 最后才是 Service 内部的内容兜底校验(400)，正常情况下到不了

    ⇒ 所以「非互关 + 空内容」返回的是 **400**（① 先拦），不是 403。
"""

from __future__ import annotations

import pytest

NOT_MUTUAL_MESSAGE = "需要互相关注后才能私聊"

# 种子里固定的 id，与 database/data.sql 一一对应
SEED_BANNED_ID = 12  # banned001，状态禁用
UNKNOWN_USER_ID = "99999999"


# ---------- 工具 ----------


def _uid(client) -> str:
    response = client.get("/api/users/me")
    assert response.ok, response.message
    return response.data["id"]


def _make_mutual(a, b) -> None:
    """建立双向关注。幂等：重复关注返回 409 视为该方向已就绪。"""
    for follower, target in ((a, b), (b, a)):
        response = follower.post(f"/api/users/{_uid(target)}/follow")
        assert response.ok or response.code == 409, response


def _break_mutual(a, b) -> None:
    """解除双向关注。幂等：本来就没关注返回 404 视为该方向已解除。"""
    for follower, target in ((a, b), (b, a)):
        response = follower.delete(f"/api/users/{_uid(target)}/follow")
        assert response.ok or response.code == 404, response


def _send(sender, receiver_id, content="你好"):
    return sender.post("/api/messages", json_body={"receiverId": receiver_id, "content": content})


def _unread_total(client) -> int:
    response = client.get("/api/messages/unread-count")
    assert response.ok, response.message
    return response.data["total"]


# ---------- 允许发送 ----------


@pytest.mark.smoke
@pytest.mark.write
def test_mutual_follow_allows_sending(fresh_users):
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)

    response = _send(sender, _uid(receiver))

    assert response.ok, response.message


# ---------- 拒绝发送 ----------


@pytest.mark.smoke
@pytest.mark.write
def test_stranger_cannot_send(fresh_users):
    """完全没有关注关系 → 403。"""
    sender, stranger = fresh_users(2)

    response = _send(sender, _uid(stranger))

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_one_way_follow_cannot_send(fresh_users):
    """只关注了对方、对方没回关 → 依然不能发。

    单向关注是这条规则最容易被漏掉的分支：发送者"看得见"对方，
    但互关是**双向**条件。
    """
    sender, receiver = fresh_users(2)
    sender.post(f"/api/users/{_uid(receiver)}/follow")

    response = _send(sender, _uid(receiver))

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_receiver_following_sender_only_cannot_send(fresh_users):
    """反方向单向关注（对方关注了我）同样不构成互关。"""
    sender, receiver = fresh_users(2)
    receiver.post(f"/api/users/{_uid(sender)}/follow")

    response = _send(sender, _uid(receiver))

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_cannot_send_after_unfollow(fresh_users):
    """取消互关后，原本能发的会话立刻不能发。"""
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)
    assert _send(sender, _uid(receiver), "互关期间发的消息").ok

    _break_mutual(sender, receiver)
    response = _send(sender, _uid(receiver), "取消互关后不该成功")

    assert response.code == 403
    assert response.message == NOT_MUTUAL_MESSAGE


@pytest.mark.write
def test_sending_is_restored_after_refollow(fresh_users):
    """重新互关后恢复发送 —— 与关注/点赞一致，关系是物理删除，可以重新建立。"""
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)
    _send(sender, _uid(receiver), "第一次互关")

    _break_mutual(sender, receiver)
    assert _send(sender, _uid(receiver)).code == 403

    _make_mutual(sender, receiver)
    restored = _send(sender, _uid(receiver), "重新互关后恢复发送")

    assert restored.ok, restored.message


# ---------- 历史消息仍然可读 ----------


@pytest.mark.write
def test_history_stays_readable_after_unfollow(fresh_users):
    """取消互关只挡"新消息"，历史记录必须照常可读。"""
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)
    _send(sender, _uid(receiver), "解除互关之前说的话")

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    _break_mutual(sender, receiver)

    history = receiver.get(f"/api/conversations/{conversation_id}/messages")

    assert history.ok, history.message
    assert [item["content"] for item in history.data] == ["解除互关之前说的话"]


@pytest.mark.write
def test_mark_read_still_works_after_unfollow(fresh_users):
    """取消互关后仍能标记已读，否则未读角标会永远清不掉。"""
    sender, receiver = fresh_users(2)
    _make_mutual(sender, receiver)
    _send(sender, _uid(receiver), "一条未读")

    conversation_id = receiver.get("/api/conversations").data["list"][0]["id"]
    _break_mutual(sender, receiver)

    assert receiver.put(f"/api/conversations/{conversation_id}/read").code == 200
    assert _unread_total(receiver) == 0


# ---------- 被拦下的请求不留下任何痕迹 ----------


@pytest.mark.write
def test_blocked_send_leaves_no_trace(fresh_users):
    """非互关发送被拒后：不产生会话、不产生未读、不产生消息。

    实现上互关校验位于写库之前，这条用例就是防止有人把校验挪到写库之后——
    那样虽然接口同样报 403，但垃圾数据已经落库了。
    """
    sender, receiver = fresh_users(2)
    before_unread = _unread_total(receiver)

    assert _send(sender, _uid(receiver)).code == 403

    assert sender.get("/api/conversations").data["total"] == 0
    assert receiver.get("/api/conversations").data["total"] == 0
    assert _unread_total(receiver) == before_unread


# ---------- 校验顺序 ----------


@pytest.mark.write
def test_dto_validation_precedes_mutual_check(fresh_users):
    """非互关 + 空内容 → 400「消息内容不能为空」，不是 403。

    说明校验链条的**最前面是 DTO 上的 Bean Validation**（`MessageSendRequest.content`
    标了 `@NotBlank`），它比 Service 层的任何校验都早。
    互关校验虽然排在 Service 内部校验的最前面，但轮不到它出手。
    """
    sender, receiver = fresh_users(2)

    response = _send(sender, _uid(receiver), "   ")

    assert response.code == 400
    assert response.message == "消息内容不能为空"


@pytest.mark.write
def test_mutual_check_precedes_length_check(fresh_users):
    """非互关 + 超长内容（DTO 的 @Size 先拦）→ 400，同样到不了互关校验。"""
    sender, receiver = fresh_users(2)

    response = _send(sender, _uid(receiver), "字" * 1001)

    assert response.code == 400


@pytest.mark.write
def test_self_check_precedes_mutual_check(fresh_user):
    """给自己发 → 400，优先级高于互关校验（自己和自己永远不构成互关）。"""
    client = fresh_user()
    user_id = _uid(client)

    response = _send(client, user_id)

    assert response.code == 400
    assert response.message == "不能给自己发送私信"


@pytest.mark.write
def test_unknown_receiver_precedes_mutual_check(fresh_user):
    """接收者不存在 → 404，优先级高于互关校验。"""
    client = fresh_user()

    response = _send(client, UNKNOWN_USER_ID)

    assert response.code == 404
    assert response.message == "用户不存在"


@pytest.mark.write
def test_disabled_receiver_precedes_mutual_check(fresh_user):
    """接收者被禁用 → 423，优先级高于互关校验。"""
    client = fresh_user()

    response = _send(client, SEED_BANNED_ID)

    assert response.code == 423
    assert response.message == "账号已被禁用，请联系管理员"
