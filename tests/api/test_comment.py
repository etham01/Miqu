"""评论接口。"""

from __future__ import annotations

import pytest


@pytest.mark.smoke
@pytest.mark.write
def test_create_comment_increments_count(fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "评论测试"}).data["id"]

    response = client.post(f"/api/posts/{post_id}/comments", json_body={"content": "第一条评论"})

    assert response.ok, response.message
    assert response.data["content"] == "第一条评论"
    assert response.data["postId"] == post_id
    assert response.data["mine"] is True

    assert client.get(f"/api/posts/{post_id}").data["commentCount"] == 1


@pytest.mark.write
def test_comment_blank_content(fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "空评论"}).data["id"]

    response = client.post(f"/api/posts/{post_id}/comments", json_body={"content": "   "})

    assert response.code == 400
    assert response.message == "评论内容不能为空"


@pytest.mark.write
def test_comment_content_boundaries(fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "评论长度边界"}).data["id"]

    too_long = client.post(f"/api/posts/{post_id}/comments", json_body={"content": "评" * 501})
    assert too_long.code == 400
    assert "500" in too_long.message

    exactly_max = client.post(f"/api/posts/{post_id}/comments", json_body={"content": "评" * 500})
    assert exactly_max.ok, exactly_max.message


@pytest.mark.write
def test_comment_on_missing_post(fresh_user):
    client = fresh_user()
    response = client.post("/api/posts/99999999/comments", json_body={"content": "评论"})

    assert response.code == 404


def test_comment_requires_login(anonymous):
    assert anonymous.post("/api/posts/1/comments", json_body={"content": "x"}).code == 401


@pytest.mark.smoke
@pytest.mark.write
def test_comment_list_is_ordered_ascending(fresh_user):
    """评论按时间正序返回，读起来像对话。"""
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "评论排序"}).data["id"]

    for index in range(3):
        client.post(f"/api/posts/{post_id}/comments", json_body={"content": f"第 {index} 条"})

    data = client.get(f"/api/posts/{post_id}/comments").data
    assert data["total"] == 3

    contents = [item["content"] for item in data["list"]]
    assert contents == ["第 0 条", "第 1 条", "第 2 条"]

    times = [item["createTime"] for item in data["list"]]
    assert times == sorted(times)


@pytest.mark.read
def test_seed_post_has_five_comments(anonymous):
    data = anonymous.get("/api/posts/1/comments", params={"size": 50}).data

    assert data["total"] == 5
    assert data["list"][0]["id"] == "1", "正序时最早的一条排在最前"


@pytest.mark.read
def test_guest_sees_mine_false(anonymous):
    data = anonymous.get("/api/posts/1/comments").data

    for item in data["list"]:
        assert item["mine"] is False


@pytest.mark.write
def test_delete_own_comment_decrements_count(fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "删除评论"}).data["id"]
    comment_id = client.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "待删除"}
    ).data["id"]

    assert client.get(f"/api/posts/{post_id}").data["commentCount"] == 1

    assert client.delete(f"/api/comments/{comment_id}").code == 200
    assert client.get(f"/api/posts/{post_id}").data["commentCount"] == 0


@pytest.mark.write
def test_delete_others_comment_is_forbidden(fresh_users):
    author, commenter = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "他人评论"}).data["id"]
    comment_id = commenter.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "别人的评论"}
    ).data["id"]

    response = author.delete(f"/api/comments/{comment_id}")
    assert response.code == 403
    assert response.message == "无权限执行该操作"


@pytest.mark.write
def test_admin_can_delete_any_comment(admin_client, fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "管理员删评论"}).data["id"]
    comment_id = client.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "将被删除"}
    ).data["id"]

    assert admin_client.delete(f"/api/comments/{comment_id}").code == 200
    assert client.get(f"/api/posts/{post_id}").data["commentCount"] == 0


@pytest.mark.write
def test_delete_comment_twice(fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "重复删除"}).data["id"]
    comment_id = client.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "删两次"}
    ).data["id"]

    assert client.delete(f"/api/comments/{comment_id}").code == 200

    second = client.delete(f"/api/comments/{comment_id}")
    assert second.code == 404
    assert second.message == "评论不存在或已被删除"


def test_delete_comment_requires_login(anonymous):
    assert anonymous.delete("/api/comments/1").code == 401
