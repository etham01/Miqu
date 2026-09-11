"""动态接口：发布、浏览、删除，以及点赞。"""

from __future__ import annotations

import pytest

from conftest import SEED_USER

VALID_IMAGE = "/uploads/image/2026/09/qa-test.jpg"


# ==================== 发布 ====================


@pytest.mark.smoke
@pytest.mark.write
def test_create_text_post(fresh_user):
    client = fresh_user()
    response = client.post("/api/posts", json_body={"content": "接口自动化测试发布的动态"})

    assert response.ok, response.message
    data = response.data
    assert data["content"] == "接口自动化测试发布的动态"
    assert data["likeCount"] == 0
    assert data["commentCount"] == 0
    assert data["images"] == []
    assert data["mine"] is True
    assert data["likedByMe"] is False


@pytest.mark.write
def test_create_post_with_images_keeps_order(fresh_user):
    client = fresh_user()
    images = [f"{VALID_IMAGE}?idx={i}" for i in range(3)]
    response = client.post("/api/posts", json_body={"content": "带图", "images": images})

    assert response.ok, response.message
    assert response.data["images"] == images


@pytest.mark.write
def test_create_post_content_and_images_both_empty(fresh_user):
    client = fresh_user()
    response = client.post("/api/posts", json_body={"content": "  ", "images": []})

    assert response.code == 400
    assert response.message == "动态内容与图片不能同时为空"


@pytest.mark.write
def test_create_post_content_too_long(fresh_user):
    client = fresh_user()
    response = client.post("/api/posts", json_body={"content": "测" * 1001})

    assert response.code == 400
    assert "1000" in response.message


@pytest.mark.write
def test_create_post_content_exactly_max_length(fresh_user):
    client = fresh_user()
    response = client.post("/api/posts", json_body={"content": "测" * 1000})

    assert response.ok, response.message


@pytest.mark.write
def test_create_post_too_many_images(fresh_user):
    client = fresh_user()
    response = client.post(
        "/api/posts",
        json_body={"images": [f"{VALID_IMAGE}?i={i}" for i in range(10)]},
    )

    assert response.code == 400


@pytest.mark.write
def test_create_post_rejects_external_image_url(fresh_user):
    """外链图片必须被拒绝，否则服务器会沦为别人的图床，内容也无从审核。"""
    client = fresh_user()
    response = client.post(
        "/api/posts",
        json_body={"content": "外链", "images": ["https://evil.example.com/tracker.gif"]},
    )

    assert response.code == 400
    assert response.message == "图片地址不合法，请先通过上传接口获取"


def test_create_post_requires_login(anonymous):
    response = anonymous.post("/api/posts", json_body={"content": "未登录"})
    assert response.code == 401


# ==================== 浏览 ====================


@pytest.mark.smoke
@pytest.mark.read
def test_list_latest_as_guest(anonymous):
    response = anonymous.get("/api/posts", params={"page": 1, "size": 10})

    assert response.ok, response.message
    data = response.data
    assert data["page"] == 1
    assert data["size"] == 10
    assert len(data["list"]) <= 10
    assert data["total"] >= 40, "种子数据有 40 条动态"


@pytest.mark.read
def test_list_latest_guest_flags_are_false(anonymous):
    data = anonymous.get("/api/posts", params={"size": 5}).data

    for item in data["list"]:
        assert item["likedByMe"] is False
        assert item["mine"] is False


@pytest.mark.read
def test_list_latest_is_ordered_by_time_desc(anonymous):
    data = anonymous.get("/api/posts", params={"size": 20}).data
    times = [item["createTime"] for item in data["list"]]

    assert times == sorted(times, reverse=True)


@pytest.mark.read
def test_list_invalid_tab(anonymous):
    response = anonymous.get("/api/posts", params={"tab": "hot"})

    assert response.code == 400
    assert response.message == "tab 取值只能是 latest 或 following"


@pytest.mark.read
def test_list_size_over_limit(anonymous):
    response = anonymous.get("/api/posts", params={"size": 1000})

    assert response.code == 400
    assert response.message == "每页条数不能超过 50"


def test_following_tab_requires_login(anonymous):
    response = anonymous.get("/api/posts", params={"tab": "following"})
    assert response.code == 401


@pytest.mark.read
def test_post_detail(anonymous):
    response = anonymous.get("/api/posts/1")

    assert response.ok, response.message
    data = response.data
    # 种子数据里 post 1 恰好 9 张图、10 个赞、5 条评论
    assert len(data["images"]) == 9
    assert data["likeCount"] == 10
    assert data["commentCount"] == 5


@pytest.mark.read
def test_post_detail_not_found(anonymous):
    response = anonymous.get("/api/posts/99999999")

    assert response.code == 404
    assert response.message == "动态不存在或已被删除"


@pytest.mark.read
def test_post_images_are_ordered(anonymous):
    images = anonymous.get("/api/posts/1").data["images"]

    # data.sql 生成的 URL 里带序号：miqu1_0 … miqu1_8
    for index, url in enumerate(images):
        assert f"miqu1_{index}/" in url


# ==================== 删除 ====================


@pytest.mark.write
def test_delete_own_post(fresh_user):
    client = fresh_user()
    created = client.post("/api/posts", json_body={"content": "待删除"})
    post_id = created.data["id"]

    assert client.delete(f"/api/posts/{post_id}").code == 200
    assert client.get(f"/api/posts/{post_id}").code == 404


@pytest.mark.write
def test_delete_others_post_is_forbidden(fresh_users):
    author, other = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "别人的动态"}).data["id"]

    response = other.delete(f"/api/posts/{post_id}")
    assert response.code == 403
    assert response.message == "无权限执行该操作"


@pytest.mark.write
def test_admin_can_delete_any_post(admin_client, fresh_user):
    author = fresh_user()
    post_id = author.post("/api/posts", json_body={"content": "管理员将删除"}).data["id"]

    assert admin_client.delete(f"/api/posts/{post_id}").code == 200
    assert author.get(f"/api/posts/{post_id}").code == 404


def test_delete_post_requires_login(anonymous):
    assert anonymous.delete("/api/posts/1").code == 401


# ==================== 点赞 ====================


@pytest.mark.smoke
@pytest.mark.write
def test_like_and_unlike(fresh_users):
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "点赞测试"}).data["id"]

    liked = liker.post(f"/api/posts/{post_id}/like")
    assert liked.ok, liked.message
    assert liked.data == {"liked": True, "likeCount": 1}

    unliked = liker.delete(f"/api/posts/{post_id}/like")
    assert unliked.ok, unliked.message
    assert unliked.data == {"liked": False, "likeCount": 0}


@pytest.mark.write
def test_duplicate_like_returns_conflict(fresh_users):
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "重复点赞"}).data["id"]

    assert liker.post(f"/api/posts/{post_id}/like").code == 200

    duplicate = liker.post(f"/api/posts/{post_id}/like")
    assert duplicate.code == 409
    assert duplicate.message == "已经点赞过该动态"


@pytest.mark.write
def test_unlike_without_like_returns_not_found(fresh_users):
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "取消未点赞"}).data["id"]

    response = liker.delete(f"/api/posts/{post_id}/like")
    assert response.code == 404
    assert response.message == "尚未点赞该动态"


@pytest.mark.smoke
@pytest.mark.write
def test_like_again_after_unlike(fresh_users):
    """取消点赞后必须能再次点赞。

    这条守护的是"关系表物理删除"这一设计决策：如果 post_like 用了逻辑删除，
    取消后唯一键 uk_post_user 仍被占住，第二次点赞会直接撞唯一键。
    """
    author, liker = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "反复点赞"}).data["id"]

    for _ in range(3):
        liked = liker.post(f"/api/posts/{post_id}/like")
        assert liked.ok, f"点赞失败：{liked.message}"
        assert liked.data["likeCount"] == 1

        unliked = liker.delete(f"/api/posts/{post_id}/like")
        assert unliked.ok, f"取消点赞失败：{unliked.message}"
        assert unliked.data["likeCount"] == 0


@pytest.mark.write
def test_like_count_accumulates_across_users(fresh_users, fresh_user):
    author = fresh_user()
    post_id = author.post("/api/posts", json_body={"content": "多人点赞"}).data["id"]

    likers = fresh_users(3)
    for index, liker in enumerate(likers, start=1):
        response = liker.post(f"/api/posts/{post_id}/like")
        assert response.data["likeCount"] == index

    likes = author.get(f"/api/posts/{post_id}/likes")
    assert likes.data["total"] == 3


def test_like_requires_login(anonymous):
    assert anonymous.post("/api/posts/1/like").code == 401


@pytest.mark.read
def test_liked_by_me_flag(seed_user_client):
    """test001 是 post 1 的作者，种子数据里 user 2~11 都点过赞。"""
    data = seed_user_client.get("/api/posts/1").data

    assert data["mine"] is True
    assert data["likedByMe"] is True


@pytest.mark.write
def test_user_posts_listing(fresh_user):
    client = fresh_user()
    me = client.get("/api/users/me").data

    for index in range(3):
        client.post("/api/posts", json_body={"content": f"第 {index} 条"})

    data = client.get(f"/api/users/{me['id']}/posts").data
    assert data["total"] == 3
    for item in data["list"]:
        assert item["author"]["id"] == me["id"]


@pytest.mark.read
def test_seed_user_has_posts(anonymous):
    response = anonymous.get("/api/users/2/posts", params={"size": 50})

    assert response.ok
    assert response.data["total"] == 3, "data.sql 里 test001 有 3 条动态"


def test_seed_user_login_is_stable(seed_user_client):
    """种子账号可用性自检：其他用例大量依赖它。"""
    me = seed_user_client.get("/api/users/me")
    assert me.ok
    assert me.data["username"] == SEED_USER
