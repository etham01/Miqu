"""用户搜索接口。"""

from __future__ import annotations

import pytest

SEED_NICKNAME = "张三"
SEED_USERNAME = "test001"


@pytest.mark.smoke
@pytest.mark.read
def test_search_by_nickname(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": SEED_NICKNAME})

    assert response.ok, response.message
    usernames = [item["username"] for item in response.data["list"]]
    assert SEED_USERNAME in usernames


@pytest.mark.read
def test_search_by_username_prefix(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": "test00", "size": 50})

    assert response.ok
    assert response.data["total"] >= 9
    for item in response.data["list"]:
        assert "test00" in item["username"] or "test00" in item["nickname"]


@pytest.mark.read
def test_search_result_shape(anonymous):
    item = anonymous.get("/api/users/search", params={"keyword": SEED_USERNAME}).data["list"][0]

    assert "email" not in item
    assert "password" not in item
    assert isinstance(item["followerCount"], int)
    assert isinstance(item["followedByMe"], bool)


@pytest.mark.read
def test_search_results_ordered_by_follower_count(anonymous):
    """粉丝多的排前面：搜索结果里"谁都认识的人"更应该被看到。"""
    data = anonymous.get("/api/users/search", params={"keyword": "test00", "size": 50}).data
    counts = [item["followerCount"] for item in data["list"]]

    assert counts == sorted(counts, reverse=True)


@pytest.mark.read
def test_search_no_match(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": "zzzznotexist"})

    assert response.ok
    assert response.data["total"] == 0
    assert response.data["list"] == []


# ==================== LIKE 通配符转义 ====================


@pytest.mark.smoke
@pytest.mark.read
def test_percent_is_escaped(anonymous):
    """`%` 必须被当作普通字符。

    不转义时它会作为 LIKE 的通配符生效，搜一个 `%` 就能把全部用户翻出来——
    既是信息泄漏，也是典型的注入式输入。
    这里断言"结果里不含 test001"：未转义时 test001 必然出现在结果中。
    """
    response = anonymous.get("/api/users/search", params={"keyword": "%", "size": 50})

    assert response.ok
    usernames = [item["username"] for item in response.data["list"]]
    assert SEED_USERNAME not in usernames, "百分号未被转义，搜索返回了全部用户"


@pytest.mark.read
def test_underscore_is_escaped(anonymous):
    """`_` 必须被当作普通字符，而不是"匹配任意单字符"。"""
    response = anonymous.get("/api/users/search", params={"keyword": "_", "size": 50})

    assert response.ok
    usernames = [item["username"] for item in response.data["list"]]
    assert SEED_USERNAME not in usernames


@pytest.mark.read
def test_wrapped_wildcard_is_escaped(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": "%test00%", "size": 50})

    assert response.ok
    usernames = [item["username"] for item in response.data["list"]]
    assert SEED_USERNAME not in usernames


@pytest.mark.read
def test_backslash_does_not_break_query(anonymous):
    """反斜杠是 ESCAPE 字符本身，必须被转义，否则会破坏 SQL 的 ESCAPE 子句。"""
    response = anonymous.get("/api/users/search", params={"keyword": "\\", "size": 50})

    assert response.ok, f"反斜杠关键词导致接口异常：{response.message}"


# ==================== 参数校验 ====================


@pytest.mark.read
def test_blank_keyword(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": ""})

    assert response.code == 400
    assert response.message == "搜索关键词不能为空"


@pytest.mark.read
def test_missing_keyword(anonymous):
    assert anonymous.get("/api/users/search").code == 400


@pytest.mark.read
def test_keyword_too_long(anonymous):
    response = anonymous.get("/api/users/search", params={"keyword": "a" * 33})

    assert response.code == 400
    assert response.message == "搜索关键词不能超过 32 个字符"


@pytest.mark.read
def test_search_is_public(anonymous):
    """搜索在免登录白名单里。"""
    assert anonymous.get("/api/users/search", params={"keyword": "test"}).code == 200


@pytest.mark.read
def test_guest_follow_flags_are_false(anonymous):
    data = anonymous.get("/api/users/search", params={"keyword": "test00", "size": 50}).data

    for item in data["list"]:
        assert item["followedByMe"] is False


@pytest.mark.write
def test_followed_by_me_reflects_real_relation(fresh_users):
    alice, bob, carol = fresh_users(3)
    bob_id = bob.get("/api/users/me").data["id"]
    bob_username = bob.get("/api/users/me").data["username"]
    carol_id = carol.get("/api/users/me").data["id"]
    carol_username = carol.get("/api/users/me").data["username"]

    alice.post(f"/api/users/{bob_id}/follow")

    data = alice.get("/api/users/search", params={"keyword": "qa", "size": 50}).data
    flags = {item["username"]: item["followedByMe"] for item in data["list"]}

    assert flags.get(bob_username) is True
    assert flags.get(carol_username) is False
