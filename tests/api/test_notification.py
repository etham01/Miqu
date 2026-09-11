"""通知接口。

通知在 P2 由关注/点赞/评论三个动作产生，查询接口在 P3 提供。
**私信不产生通知** —— 它由会话未读数承载，避免同一件事在两个地方重复出现。
"""

from __future__ import annotations

import pytest

TYPE_FOLLOW = 1
TYPE_LIKE = 2
TYPE_COMMENT = 3


def _unread(client) -> dict:
    response = client.get("/api/notifications/unread-count")
    assert response.ok, response.message
    return response.data


def _list(client, **params) -> dict:
    response = client.get("/api/notifications", params={"size": 50, **params})
    assert response.ok, response.message
    return response.data


def test_notifications_require_login(anonymous):
    assert anonymous.get("/api/notifications").code == 401
    assert anonymous.get("/api/notifications/unread-count").code == 401


@pytest.mark.write
def test_follow_creates_notification(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    before = _unread(target)["follow"]
    follower.post(f"/api/users/{target_id}/follow")

    assert _unread(target)["follow"] == before + 1

    latest = _list(target, type=TYPE_FOLLOW)["list"][0]
    assert latest["type"] == TYPE_FOLLOW
    assert latest["isRead"] is False
    assert latest["actor"]["id"] == follower.get("/api/users/me").data["id"]


@pytest.mark.write
def test_unfollow_removes_notification(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    after_follow = _unread(target)["follow"]

    follower.delete(f"/api/users/{target_id}/follow")

    assert _unread(target)["follow"] == after_follow - 1


@pytest.mark.write
def test_like_creates_notification(fresh_users):
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "点赞通知"}).data["id"]

    before = _unread(author)["like"]
    liker.post(f"/api/posts/{post_id}/like")

    assert _unread(author)["like"] == before + 1

    latest = _list(author, type=TYPE_LIKE)["list"][0]
    assert latest["postId"] == post_id


@pytest.mark.write
def test_unlike_removes_notification(fresh_users):
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "取消点赞通知"}).data["id"]

    liker.post(f"/api/posts/{post_id}/like")
    after_like = _unread(author)["like"]

    liker.delete(f"/api/posts/{post_id}/like")

    assert _unread(author)["like"] == after_like - 1


@pytest.mark.write
def test_comment_creates_notification_with_snapshot(fresh_users):
    author, commenter = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "评论通知"}).data["id"]

    before = _unread(author)["comment"]
    commenter.post(f"/api/posts/{post_id}/comments", json_body={"content": "通知快照内容"})

    assert _unread(author)["comment"] == before + 1

    latest = _list(author, type=TYPE_COMMENT)["list"][0]
    assert latest["content"] == "通知快照内容"
    assert latest["postId"] == post_id
    assert latest["commentId"]


@pytest.mark.write
def test_self_actions_do_not_notify(fresh_user):
    """关注/点赞/评论自己不该给自己发通知。"""
    client = fresh_user()
    me = client.get("/api/users/me").data
    post_id = client.post("/api/posts", json_body={"content": "自己的动态"}).data["id"]

    before = _unread(client)

    client.post(f"/api/posts/{post_id}/like")
    client.post(f"/api/posts/{post_id}/comments", json_body={"content": "自己评论自己"})

    after = _unread(client)
    assert after["like"] == before["like"]
    assert after["comment"] == before["comment"]


@pytest.mark.smoke
@pytest.mark.write
def test_mark_single_notification_read(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    notification = _list(target, type=TYPE_FOLLOW)["list"][0]

    before = _unread(target)["total"]
    assert target.put(f"/api/notifications/{notification['id']}/read").code == 200
    assert _unread(target)["total"] == before - 1


@pytest.mark.write
def test_mark_read_is_idempotent(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    notification = _list(target, type=TYPE_FOLLOW)["list"][0]

    target.put(f"/api/notifications/{notification['id']}/read")
    after_first = _unread(target)["total"]

    # 再标记一次不应报错，也不应继续减少未读数
    assert target.put(f"/api/notifications/{notification['id']}/read").code == 200
    assert _unread(target)["total"] == after_first


@pytest.mark.write
def test_cannot_mark_others_notification(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    notification = _list(target, type=TYPE_FOLLOW)["list"][0]

    response = follower.put(f"/api/notifications/{notification['id']}/read")

    assert response.code == 403
    assert response.message == "无权限执行该操作"


@pytest.mark.smoke
@pytest.mark.write
def test_mark_all_read(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    post_id = target.post("/api/posts", json_body={"content": "全部已读"}).data["id"]
    follower.post(f"/api/posts/{post_id}/like")

    assert _unread(target)["total"] > 0
    assert target.put("/api/notifications/read-all").code == 200

    after = _unread(target)
    assert after == {"total": 0, "follow": 0, "like": 0, "comment": 0}


@pytest.mark.write
def test_mark_all_read_does_not_affect_others(fresh_users):
    follower, target, bystander = fresh_users(3)
    target_id = target.get("/api/users/me").data["id"]
    bystander_id = bystander.get("/api/users/me").data["id"]

    follower.post(f"/api/users/{target_id}/follow")
    follower.post(f"/api/users/{bystander_id}/follow")

    before_bystander = _unread(bystander)["total"]
    target.put("/api/notifications/read-all")

    assert _unread(bystander)["total"] == before_bystander


@pytest.mark.read
def test_unread_count_shape(seed_user_client):
    """未读数按类型拆开，且 total 等于三项之和。"""
    data = _unread(seed_user_client)

    assert set(data.keys()) == {"total", "follow", "like", "comment"}
    assert data["total"] == data["follow"] + data["like"] + data["comment"]
    for value in data.values():
        assert isinstance(value, int), "未读数必须是数字而不是字符串"


@pytest.mark.read
def test_notification_type_filter(seed_user_client):
    """只返回关注/点赞/评论三类，不存在私信类通知。"""
    for notification in _list(seed_user_client)["list"]:
        assert notification["type"] in (TYPE_FOLLOW, TYPE_LIKE, TYPE_COMMENT)


@pytest.mark.read
def test_notification_actor_has_no_email(seed_user_client):
    first = _list(seed_user_client)["list"][0]

    assert "email" not in first["actor"]
    assert first["actor"]["nickname"]


@pytest.mark.read
def test_invalid_type_filter(seed_user_client):
    response = seed_user_client.get("/api/notifications", params={"type": 9})

    assert response.code == 400
    assert response.message == "通知类型只能是 1、2 或 3"
