"""认证接口：注册、登录、退出。"""

from __future__ import annotations

import pytest

from conftest import SEED_BANNED, SEED_DELETED, SEED_USER


@pytest.mark.smoke
@pytest.mark.write
def test_register_success(anonymous, unique_suffix):
    username = f"qa{unique_suffix}"
    response = anonymous.register(username)

    assert response.code == 200, response.message
    assert response.data["username"] == username
    # 密码绝不出现在响应里
    assert "password" not in response.data
    assert response.data["role"] == 1, "新注册用户必须是普通用户"


@pytest.mark.write
def test_register_duplicate_username(anonymous, unique_suffix):
    username = f"qa{unique_suffix}"
    assert anonymous.register(username).code == 200

    duplicate = anonymous.register(username)
    assert duplicate.code == 409
    assert duplicate.message == "用户名已被占用"


@pytest.mark.write
def test_register_duplicate_email_is_case_insensitive(anonymous, unique_suffix):
    username_a = f"qa{unique_suffix}"
    email = f"{username_a}@miqu.test"
    assert anonymous.register(username_a, email=email).code == 200

    # 邮箱大小写不同，但归一化后应当判定为同一个
    duplicate = anonymous.register(f"qb{unique_suffix}", email=email.upper())
    assert duplicate.code == 409


@pytest.mark.parametrize(
    ("field", "value", "expected_message"),
    [
        ("username", "abc", "用户名长度必须在 4~20 个字符之间"),
        ("username", "1abcdef", "用户名必须以字母开头，且只能包含字母、数字和下划线"),
        ("password", "12345", "密码长度必须在 6~20 个字符之间"),
        ("email", "not-an-email", "邮箱格式不正确"),
    ],
)
@pytest.mark.write
def test_register_parameter_validation(anonymous, unique_suffix, field, value, expected_message):
    """逐个字段构造只违反一条约束的输入，这样返回的文案是确定的。

    空值会同时违反多条约束（@NotBlank / @Size / @Pattern），而校验器不保证触发顺序，
    因此那种情况只断言错误码，见下一个用例。
    """
    payload = {
        "username": f"qa{unique_suffix}",
        "password": "123456",
        "nickname": "参数校验",
        "email": f"qa{unique_suffix}@miqu.test",
    }
    payload[field] = value

    response = anonymous.post("/api/auth/register", json_body=payload)
    assert response.code == 400
    assert response.message == expected_message


@pytest.mark.write
def test_register_blank_username_only_checks_code(anonymous, unique_suffix):
    response = anonymous.post(
        "/api/auth/register",
        json_body={
            "username": "",
            "password": "123456",
            "nickname": "空用户名",
            "email": f"qa{unique_suffix}@miqu.test",
        },
    )
    assert response.code == 400


@pytest.mark.smoke
def test_login_success(anonymous):
    response = anonymous.login(SEED_USER)

    assert response.code == 200
    assert response.data["token"]
    assert response.data["tokenType"] == "Bearer"
    assert response.data["expiresIn"] > 0
    assert response.data["user"]["username"] == SEED_USER
    assert "password" not in response.data["user"]


def test_login_wrong_password(anonymous):
    response = anonymous.login(SEED_USER, "definitely-wrong")

    assert response.code == 401
    assert response.message == "用户名或密码错误"


def test_login_unknown_user_returns_same_message(anonymous):
    """用户不存在与密码错误必须返回完全相同的响应，否则可被用来枚举用户名。"""
    unknown = anonymous.login("nosuchuser000", "123456")
    wrong_password = anonymous.login(SEED_USER, "wrong")

    assert unknown.code == wrong_password.code == 401
    assert unknown.message == wrong_password.message


def test_login_disabled_user(anonymous):
    response = anonymous.login(SEED_BANNED)

    assert response.code == 423
    assert "禁用" in response.message


def test_login_deleted_user(anonymous):
    """已注销用户被逻辑删除，登录时表现为"用户不存在"。"""
    response = anonymous.login(SEED_DELETED)

    assert response.code == 401


@pytest.mark.parametrize("payload", [{"username": "", "password": "123456"}, {"username": SEED_USER, "password": ""}])
def test_login_blank_parameters(anonymous, payload):
    response = anonymous.post("/api/auth/login", json_body=payload)
    assert response.code == 400


def test_me_requires_login(anonymous):
    response = anonymous.get("/api/users/me")
    assert response.code == 401


def test_me_returns_403_after_account_disabled(admin_client, fresh_user):
    """禁用立即生效：已签发的旧 Token 会马上失效。

    这条依赖后端在 JWT 过滤器里**每请求查库校验状态**。
    如果只验签名不查库，被禁用的用户拿着旧 Token 在 7 天内都能继续访问。
    """
    victim = fresh_user()
    me = victim.get("/api/users/me")
    assert me.code == 200
    victim_id = me.data["id"]

    try:
        disabled = admin_client.put(f"/api/admin/users/{victim_id}/status", json_body={"status": 0})
        assert disabled.code == 200

        # 旧 Token 立即失效
        assert victim.get("/api/users/me").code == 423
        # 也无法重新登录
        assert victim.login(me.data["username"], "123456").code == 423
    finally:
        # 恢复状态，避免这条用例给后续运行留下一个禁用账号
        admin_client.put(f"/api/admin/users/{victim_id}/status", json_body={"status": 1})


def test_logout_requires_login(anonymous):
    response = anonymous.post("/api/auth/logout")
    assert response.code == 401


def test_logout_with_token(seed_user_client):
    response = seed_user_client.post("/api/auth/logout")
    assert response.code == 200
