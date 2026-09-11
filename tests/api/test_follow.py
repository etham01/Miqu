"""关注关系接口。"""

from __future__ import annotations

import pytest

from conftest import SEED_BANNED, SEED_DELETED, SEED_USER, SEED_USER_2

# 种子里固定的 id，与 database/data.sql 一一对应
SEED_BANNED_ID = 12  # banned001，状态禁用
DELETED_USER_ID = 13  # deleted001，逻辑删除
SEED_USER_2_ID = 3  # test002，与 test001 互相关注
UNKNOWN_USER_ID = "99999999"


@pytest.mark.smoke
@pytest.mark.write
def test_follow_and_unfollow(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    followed = follower.post(f"/api/users/{target_id}/follow")
    assert followed.ok, followed.message
    assert followed.data["following"] is True
    assert followed.data["followerCount"] == 1

    unfollowed = follower.delete(f"/api/users/{target_id}/follow")
    assert unfollowed.ok, unfollowed.message
    assert unfollowed.data["following"] is False
    assert unfollowed.data["followerCount"] == 0


@pytest.mark.write
def test_duplicate_follow_returns_conflict(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]
    follower.post(f"/api/users/{target_id}/follow")

    duplicate = follower.post(f"/api/users/{target_id}/follow")
    assert duplicate.code == 409
    assert duplicate.message == "已经关注过该用户"


@pytest.mark.smoke
@pytest.mark.write
def test_follow_again_after_unfollow(fresh_users):
    """取消关注后必须能重新关注 —— 同样是为了验证 follow 表是物理删除。"""
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    for _ in range(3):
        assert follower.post(f"/api/users/{target_id}/follow").ok
        assert follower.delete(f"/api/users/{target_id}/follow").ok


@pytest.mark.write
def test_cannot_follow_self(fresh_user):
    client = fresh_user()
    user_id = client.get("/api/users/me").data["id"]

    response = client.post(f"/api/users/{user_id}/follow")
    assert response.code == 400
    assert response.message == "不能关注自己"


@pytest.mark.write
def test_unfollow_without_following(fresh_users):
    follower, target = fresh_users(2)
    target_id = target.get("/api/users/me").data["id"]

    response = follower.delete(f"/api/users/{target_id}/follow")
    assert response.code == 404
    assert response.message == "尚未关注该用户"


@pytest.mark.write
def test_follow_disabled_user(fresh_user):
    client = fresh_user()
    # 种子数据里 banned001(id=12) 状态为禁用
    response = client.post(f"/api/users/{SEED_BANNED_ID}/follow")

    assert response.code == 423


@pytest.mark.write
def test_follow_deleted_user(fresh_user):
    client = fresh_user()
    response = client.post(f"/api/users/{DELETED_USER_ID}/follow")

    assert response.code == 404


@pytest.mark.write
def test_follow_unknown_user(fresh_user):
    client = fresh_user()
    response = client.post(f"/api/users/{UNKNOWN_USER_ID}/follow")

    assert response.code == 404


def test_follow_requires_login(anonymous):
    assert anonymous.post("/api/users/3/follow").code == 401


@pytest.mark.read
def test_following_and_follower_lists(seed_user_client):
    """test001 在种子数据里关注了 6 个人。"""
    following = seed_user_client.get("/api/users/me/following", params={"size": 50})
    assert following.ok
    assert following.data["total"] == 6

    for item in following.data["list"]:
        assert item["followedByMe"] is True


@pytest.mark.read
def test_follower_list_marks_followed_back(seed_user_client):
    """粉丝列表里的 followedByMe 决定要不要显示「回关」按钮。

    test001 有 14 个粉丝，其中 6 个（test002~test007）被他回关了。
    """
    followers = seed_user_client.get("/api/users/me/followers", params={"size": 50})
    assert followers.ok
    assert followers.data["total"] == 14

    followed_back = [item for item in followers.data["list"] if item["followedByMe"]]
    not_followed_back = [item for item in followers.data["list"] if not item["followedByMe"]]

    assert len(followed_back) == 6
    assert len(not_followed_back) == 8


@pytest.mark.read
def test_guest_sees_false_follow_flags(anonymous):
    data = anonymous.get(f"/api/users/{SEED_USER_2_ID}").data

    assert data["followedByMe"] is False
    assert data["followingMe"] is False
    assert data["mutual"] is False


@pytest.mark.read
def test_mutual_follow_is_reported(seed_user_client):
    """test001 与 test002 在种子数据里互相关注。"""
    data = seed_user_client.get(f"/api/users/{SEED_USER_2_ID}").data

    assert data["followedByMe"] is True
    assert data["followingMe"] is True
    assert data["mutual"] is True


@pytest.mark.read
def test_profile_hides_email(anonymous):
    data = anonymous.get(f"/api/users/{SEED_USER_2_ID}").data

    assert "email" not in data


@pytest.mark.read
def test_profile_not_found(anonymous):
    assert anonymous.get(f"/api/users/{UNKNOWN_USER_ID}").code == 404


@pytest.mark.read
def test_deleted_user_profile_is_not_visible(anonymous):
    assert anonymous.get(f"/api/users/{DELETED_USER_ID}").code == 404


@pytest.mark.read
def test_banned_user_profile_is_still_visible(anonymous):
    """被禁用的用户主页仍可浏览（只是无法与之互动）。"""
    response = anonymous.get(f"/api/users/{SEED_BANNED_ID}")

    assert response.ok, "被禁用用户的主页应当可见"
    assert response.data["username"] == SEED_BANNED


@pytest.mark.write
def test_following_feed_contains_only_followed_authors(fresh_users):
    """关注流里不能出现未关注者的动态。"""
    me, alice, bob = fresh_users(3)
    alice_id = alice.get("/api/users/me").data["id"]
    bob_id = bob.get("/api/users/me").data["id"]

    alice.post("/api/posts", json_body={"content": "alice 的动态"})
    bob.post("/api/posts", json_body={"content": "bob 的动态"})

    me.post(f"/api/users/{alice_id}/follow")

    feed = me.get("/api/posts", params={"tab": "following", "size": 50})
    assert feed.ok, feed.message

    author_ids = {item["author"]["id"] for item in feed.data["list"]}
    assert alice_id in author_ids, "应当包含已关注者的动态"
    assert bob_id not in author_ids, "不应包含未关注者的动态"
    assert not any(item["mine"] for item in feed.data["list"]), "关注流不应包含自己的动态"


def test_seed_constants_are_consistent():
    """防止本文件的常量与 conftest 里的账号名脱节。

    这类"常量对不上"的错误不会让任何用例失败，只会让断言悄悄失效，
    所以单独用一条用例钉住。
    """
    assert SEED_USER == "test001"
    assert SEED_USER_2 == "test002"
    assert SEED_BANNED == "banned001"
    assert SEED_DELETED == "deleted001"
