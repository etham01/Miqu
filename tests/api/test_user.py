"""用户资料接口。"""

from __future__ import annotations

import pytest

from conftest import SEED_USER


@pytest.mark.smoke
@pytest.mark.write
def test_get_current_user(fresh_user):
    client = fresh_user()
    response = client.get("/api/users/me")

    assert response.ok, response.message
    data = response.data
    assert "password" not in data, "响应中绝不能出现密码"
    assert data["role"] == 1
    # 个人中心返回的是"自己看自己"的视图，不包含后台才需要的 status 字段
    assert "status" not in data
    assert data["followingCount"] == 0
    assert data["followerCount"] == 0
    assert data["postCount"] == 0


def test_get_current_user_requires_login(anonymous):
    assert anonymous.get("/api/users/me").code == 401


@pytest.mark.smoke
@pytest.mark.write
def test_update_profile(fresh_user):
    client = fresh_user()
    response = client.put(
        "/api/users/me",
        json_body={
            "nickname": "改名后的昵称",
            "gender": 2,
            "birthday": "2000-01-01",
            "bio": "更新后的简介",
        },
    )

    assert response.ok, response.message
    data = response.data
    assert data["nickname"] == "改名后的昵称"
    assert data["gender"] == 2
    assert data["birthday"] == "2000-01-01"
    assert data["bio"] == "更新后的简介"

    # 再查一次确认已落库
    assert client.get("/api/users/me").data["nickname"] == "改名后的昵称"


@pytest.mark.write
def test_update_profile_does_not_change_username_or_email(fresh_user):
    """用户名与邮箱不可通过资料接口修改（它们有唯一约束）。"""
    client = fresh_user()
    original = client.get("/api/users/me").data

    client.put(
        "/api/users/me",
        json_body={
            "nickname": "尝试越权改用户名",
            "username": "hacked-name",
            "email": "hacked@miqu.test",
            "role": 2,
        },
    )

    after = client.get("/api/users/me").data
    assert after["username"] == original["username"]
    assert after["email"] == original["email"]
    assert after["role"] == 1, "不能通过资料接口把自己提权成管理员"


@pytest.mark.parametrize(
    ("payload", "expected_code"),
    [
        ({"nickname": ""}, 400),
        ({"nickname": "x" * 33}, 400),
        ({"nickname": "正常", "gender": 9}, 400),
        ({"nickname": "正常", "bio": "字" * 256}, 400),
    ],
)
@pytest.mark.write
def test_update_profile_validation(fresh_user, payload, expected_code):
    client = fresh_user()
    response = client.put("/api/users/me", json_body=payload)

    assert response.code == expected_code


@pytest.mark.write
def test_update_profile_requires_login(anonymous):
    response = anonymous.put("/api/users/me", json_body={"nickname": "未登录"})
    assert response.code == 401


@pytest.mark.smoke
@pytest.mark.write
def test_change_password(fresh_user):
    client = fresh_user()
    username = client.get("/api/users/me").data["username"]

    changed = client.put(
        "/api/users/me/password",
        json_body={"oldPassword": "123456", "newPassword": "newpass123"},
    )
    assert changed.ok, changed.message

    # 旧密码失效
    assert client.login(username, "123456").code == 401
    # 新密码可用
    assert client.login(username, "newpass123").code == 200


@pytest.mark.write
def test_change_password_wrong_old(fresh_user):
    client = fresh_user()
    response = client.put(
        "/api/users/me/password",
        json_body={"oldPassword": "wrong-old", "newPassword": "newpass123"},
    )

    assert response.code == 400
    assert response.message == "原密码不正确"


@pytest.mark.write
def test_change_password_same_as_old(fresh_user):
    client = fresh_user()
    response = client.put(
        "/api/users/me/password",
        json_body={"oldPassword": "123456", "newPassword": "123456"},
    )

    assert response.code == 400
    assert response.message == "新密码不能与原密码相同"


@pytest.mark.write
def test_change_password_too_short(fresh_user):
    client = fresh_user()
    response = client.put(
        "/api/users/me/password",
        json_body={"oldPassword": "123456", "newPassword": "123"},
    )

    assert response.code == 400
    assert "6~20" in response.message


@pytest.mark.write
def test_update_avatar(fresh_user):
    client = fresh_user()
    response = client.put(
        "/api/users/me/avatar",
        json_body={"avatar": "/uploads/image/2026/09/qa-avatar.jpg"},
    )

    assert response.ok, response.message
    assert response.data["avatar"] == "/uploads/image/2026/09/qa-avatar.jpg"


@pytest.mark.write
def test_update_avatar_blank(fresh_user):
    client = fresh_user()
    response = client.put("/api/users/me/avatar", json_body={"avatar": ""})

    assert response.code == 400
    assert response.message == "头像地址不能为空"


@pytest.mark.write
def test_update_avatar_rejects_external_url(fresh_user):
    """BUG-005 回归：头像只接受本项目上传接口返回的地址。

    与发布动态的图片校验对称（对照 test_post.py 的外链图片用例）。
    此前只校验了非空与长度，任意外链都能写进头像——服务器就成了别人的图床，
    违规头像也无从审核。
    """
    client = fresh_user()
    before = client.get("/api/users/me").data["avatar"]

    response = client.put(
        "/api/users/me/avatar",
        json_body={"avatar": "https://evil.example.com/track.png"},
    )

    assert response.code == 400
    assert response.message == "图片地址不合法，请先通过上传接口获取"
    # 被拒绝的写入不能产生任何副作用
    assert client.get("/api/users/me").data["avatar"] == before


@pytest.mark.write
def test_update_avatar_rejects_lookalike_prefix(fresh_user):
    """前缀必须带 "/"，否则 "/uploads-evil/a.png" 这类近似串会蒙混过关。

    与动态图片校验同款边界（见 test_post.py）。
    """
    client = fresh_user()
    before = client.get("/api/users/me").data["avatar"]

    response = client.put(
        "/api/users/me/avatar",
        json_body={"avatar": "/uploads-evil/a.png"},
    )

    assert response.code == 400
    assert response.message == "图片地址不合法，请先通过上传接口获取"
    assert client.get("/api/users/me").data["avatar"] == before


# ==================== 白名单边界 ====================


def test_me_path_is_not_whitelisted(anonymous):
    """`/api/users/me` 必须要求登录。

    白名单用的是 `{id:[0-9]+}` 而不是 `*`，正是为了不把 /me 一起放出去。
    """
    assert anonymous.get("/api/users/me").code == 401
    assert anonymous.get("/api/users/me/following").code == 401
    assert anonymous.get("/api/users/me/followers").code == 401


@pytest.mark.read
def test_numeric_user_path_is_public(anonymous):
    """数字 ID 形式的主页对游客开放。"""
    assert anonymous.get("/api/users/2").code == 200
    assert anonymous.get("/api/users/2/posts").code == 200


@pytest.mark.read
def test_seed_user_profile(anonymous):
    data = anonymous.get("/api/users/2").data

    assert data["username"] == SEED_USER
    assert data["followingCount"] == 6
    assert data["followerCount"] == 14
    assert data["postCount"] == 3
