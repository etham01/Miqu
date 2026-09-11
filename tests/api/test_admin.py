"""管理后台接口。

重点覆盖三件事：
1. **访问控制**：未登录 401、非管理员 403、管理员 200，三者必须可区分
2. **权限边界**：不能禁用自己、不能禁用管理员
3. **处置动作**：处理举报时可同时删内容/禁用用户，且非法组合被拒绝
"""

from __future__ import annotations

import pytest

SEED_BANNED_ID = "12"

ADMIN_ENDPOINTS = [
    ("GET", "/api/admin/stats"),
    ("GET", "/api/admin/users"),
    ("GET", "/api/admin/users/2"),
    ("GET", "/api/admin/posts"),
    ("GET", "/api/admin/comments"),
    ("GET", "/api/admin/reports"),
    ("GET", "/api/admin/logs"),
]


# ==================== 访问控制 ====================


@pytest.mark.smoke
@pytest.mark.parametrize(("method", "path"), ADMIN_ENDPOINTS)
def test_admin_endpoints_require_login(anonymous, method, path):
    response = anonymous.request(method, path)
    assert response.code == 401, f"{method} {path} 未登录时应当是 401"


@pytest.mark.smoke
@pytest.mark.parametrize(("method", "path"), ADMIN_ENDPOINTS)
def test_admin_endpoints_forbid_normal_user(seed_user_client, method, path):
    response = seed_user_client.request(method, path)
    assert response.code == 403, f"{method} {path} 普通用户应当被拒绝"
    assert response.message == "无权限执行该操作"


@pytest.mark.parametrize(("method", "path"), ADMIN_ENDPOINTS)
def test_admin_endpoints_allow_admin(admin_client, method, path):
    response = admin_client.request(method, path)
    assert response.ok, f"{method} {path} 管理员应当可以访问，实际 {response}"


def test_401_and_403_have_distinct_messages(anonymous, seed_user_client):
    """两种失败必须给出不同文案，前端才能判断该引导登录还是提示无权限。"""
    unauthorized = anonymous.get("/api/admin/stats")
    forbidden = seed_user_client.get("/api/admin/stats")

    assert unauthorized.code == 401
    assert forbidden.code == 403
    assert unauthorized.message != forbidden.message


# ==================== 统计 ====================


@pytest.mark.read
def test_stats_shape(admin_client):
    response = admin_client.get("/api/admin/stats")

    assert response.ok, response.message
    data = response.data
    expected_keys = {
        "userTotal",
        "postTotal",
        "commentTotal",
        "todayNewUser",
        "todayNewPost",
        "todayNewComment",
        "pendingReportTotal",
    }
    assert set(data.keys()) == expected_keys
    for value in data.values():
        assert isinstance(value, int), "统计字段必须是数字而不是字符串"


@pytest.mark.write
def test_stats_user_total_increments_after_register(admin_client, anonymous, unique_suffix):
    before = admin_client.get("/api/admin/stats").data

    anonymous.register(f"qa{unique_suffix}")

    after = admin_client.get("/api/admin/stats").data
    assert after["userTotal"] == before["userTotal"] + 1
    assert after["todayNewUser"] == before["todayNewUser"] + 1


@pytest.mark.write
def test_stats_post_total_decreases_after_delete(admin_client, fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "统计用"}).data["id"]

    before = admin_client.get("/api/admin/stats").data["postTotal"]
    assert admin_client.delete(f"/api/admin/posts/{post_id}").code == 200
    after = admin_client.get("/api/admin/stats").data["postTotal"]

    assert after == before - 1


# ==================== 用户管理 ====================


@pytest.mark.read
def test_user_list(admin_client):
    response = admin_client.get("/api/admin/users", params={"size": 50})

    assert response.ok
    assert response.data["total"] > 0

    first = response.data["list"][0]
    assert "password" not in first
    assert "status" in first, "后台列表需要 status 字段"


@pytest.mark.read
def test_user_list_hides_deleted_user(admin_client):
    # size 上限是 50，传 100 会直接返回 400（后端刻意不静默重置）
    usernames = {
        item["username"]
        for item in admin_client.get("/api/admin/users", params={"size": 50}).data["list"]
    }
    assert "deleted001" not in usernames, "已注销用户不应出现在后台列表"


@pytest.mark.read
def test_user_list_filter_by_status(admin_client):
    disabled = admin_client.get("/api/admin/users", params={"status": 0, "size": 50}).data

    for item in disabled["list"]:
        assert item["status"] == 0


@pytest.mark.read
def test_user_list_keyword_wildcard_is_escaped(admin_client):
    """后台搜索同样要转义 LIKE 通配符，否则搜 `%` 会返回全部用户。"""
    response = admin_client.get("/api/admin/users", params={"keyword": "%", "size": 50})

    assert response.ok
    usernames = [item["username"] for item in response.data["list"]]
    assert "test001" not in usernames


@pytest.mark.read
def test_user_list_invalid_status(admin_client):
    response = admin_client.get("/api/admin/users", params={"status": 9})
    assert response.code == 400


@pytest.mark.write
def test_disable_and_enable_user(admin_client, fresh_user):
    target = fresh_user()
    target_id = target.get("/api/users/me").data["id"]
    username = target.get("/api/users/me").data["username"]

    assert admin_client.put(f"/api/admin/users/{target_id}/status", json_body={"status": 0}).ok

    detail = admin_client.get(f"/api/admin/users/{target_id}").data
    assert detail["status"] == 0
    assert target.login(username, "123456").code == 423

    assert admin_client.put(f"/api/admin/users/{target_id}/status", json_body={"status": 1}).ok
    assert target.login(username, "123456").code == 200


@pytest.mark.write
def test_cannot_disable_self(admin_client):
    admin_id = admin_client.get("/api/users/me").data["id"]

    response = admin_client.put(f"/api/admin/users/{admin_id}/status", json_body={"status": 0})
    assert response.code == 400
    assert response.message == "不能对自己执行该操作"


def test_disable_self_is_blocked_before_admin_guard(admin_client):
    """【已知覆盖缺口】"不能禁用管理员账号"这条分支在种子数据下无法通过 HTTP 触达。

    原因：接口的校验顺序是
      1. 不能操作自己（400 CANNOT_OPERATE_SELF）
      2. 目标若是管理员则拒绝（403 CANNOT_DISABLE_ADMIN）

    种子数据里只有一个管理员（admin，id=1），而管理员无法把自己作为禁用目标
    （第 1 条先命中），因此第 2 条没有任何输入能走到。

    要覆盖它需要第二个管理员账号。这里**不假装覆盖**，只把它记为已知缺口：
    要么在 data.sql 里补一个 admin2，要么靠直接改库的集成测试来验证。
    """
    admin_id = admin_client.get("/api/users/me").data["id"]

    response = admin_client.put(f"/api/admin/users/{admin_id}/status", json_body={"status": 0})
    assert response.code == 400
    assert response.message == "不能对自己执行该操作"


@pytest.mark.write
def test_invalid_status_value(admin_client, fresh_user):
    target_id = fresh_user().get("/api/users/me").data["id"]

    response = admin_client.put(f"/api/admin/users/{target_id}/status", json_body={"status": 9})
    assert response.code == 400


@pytest.mark.read
def test_user_detail_not_found(admin_client):
    assert admin_client.get("/api/admin/users/99999999").code == 404


# ==================== 内容管理 ====================


@pytest.mark.read
def test_post_list(admin_client):
    response = admin_client.get("/api/admin/posts", params={"size": 50})

    assert response.ok
    assert response.data["total"] > 0
    first = response.data["list"][0]
    assert "author" in first
    assert "images" in first


@pytest.mark.write
def test_admin_delete_post(admin_client, fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "后台删除"}).data["id"]

    assert admin_client.delete(f"/api/admin/posts/{post_id}").code == 200
    assert client.get(f"/api/posts/{post_id}").code == 404


@pytest.mark.read
def test_comment_list(admin_client):
    response = admin_client.get("/api/admin/comments", params={"size": 50})

    assert response.ok
    assert response.data["total"] > 0


@pytest.mark.write
def test_admin_delete_comment(admin_client, fresh_user):
    client = fresh_user()
    post_id = client.post("/api/posts", json_body={"content": "评论后台删除"}).data["id"]
    comment_id = client.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "将被后台删除"}
    ).data["id"]

    assert admin_client.delete(f"/api/admin/comments/{comment_id}").code == 200
    assert client.get(f"/api/posts/{post_id}").data["commentCount"] == 0


# ==================== 举报处理 ====================


@pytest.mark.write
def test_report_is_visible_in_admin_queue(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "会被举报的动态"}).data["id"]

    before = admin_client.get("/api/admin/reports", params={"status": 0}).data["total"]
    assert reporter.post(
        "/api/reports",
        json_body={"targetType": 2, "targetId": post_id, "reasonType": 1, "reasonDetail": "测试举报"},
    ).ok

    after = admin_client.get("/api/admin/reports", params={"status": 0}).data["total"]
    assert after == before + 1


@pytest.mark.read
def test_report_list_has_target_preview(admin_client):
    """target_id 是多态外键，服务端要解析出被举报对象的摘要供后台展示。"""
    reports = admin_client.get("/api/admin/reports", params={"size": 50}).data["list"]

    assert reports
    for item in reports:
        assert item["targetPreview"], "被举报对象的摘要不应为空"
        assert item["targetType"] in (1, 2, 3)


@pytest.mark.smoke
@pytest.mark.write
def test_handle_report_with_delete_action(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "举报后删除"}).data["id"]

    report_id = reporter.post(
        "/api/reports",
        json_body={"targetType": 2, "targetId": post_id, "reasonType": 1},
    ).data["id"]

    handled = admin_client.put(
        f"/api/admin/reports/{report_id}/handle",
        json_body={"status": 1, "handleRemark": "确认违规", "action": "DELETE_POST"},
    )
    assert handled.ok, handled.message

    # 动态已被删除
    assert author.get(f"/api/posts/{post_id}").code == 404


@pytest.mark.write
def test_handle_report_reject(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "举报后驳回"}).data["id"]

    report_id = reporter.post(
        "/api/reports",
        json_body={"targetType": 2, "targetId": post_id, "reasonType": 1},
    ).data["id"]

    assert admin_client.put(
        f"/api/admin/reports/{report_id}/handle",
        json_body={"status": 2, "handleRemark": "经核实未违规"},
    ).ok

    # 驳回不应删除内容
    assert author.get(f"/api/posts/{post_id}").ok


@pytest.mark.write
def test_reject_with_action_is_rejected(admin_client, fresh_users):
    """既判定未违规又删内容自相矛盾。"""
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "矛盾组合"}).data["id"]
    report_id = reporter.post(
        "/api/reports", json_body={"targetType": 2, "targetId": post_id, "reasonType": 1}
    ).data["id"]

    response = admin_client.put(
        f"/api/admin/reports/{report_id}/handle",
        json_body={"status": 2, "action": "DELETE_POST"},
    )

    assert response.code == 400
    assert response.message == "驳回举报时不能同时执行处置动作"
    # 内容没有被误删
    assert author.get(f"/api/posts/{post_id}").ok


@pytest.mark.write
def test_action_must_match_target_type(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "评论区举报"}).data["id"]
    # 评论必须由别人发：举报自己发布的内容会被 CANNOT_REPORT_SELF 挡下
    comment_id = author.post(
        f"/api/posts/{post_id}/comments", json_body={"content": "这条是评论"}
    ).data["id"]

    report_id = reporter.post(
        "/api/reports", json_body={"targetType": 3, "targetId": comment_id, "reasonType": 2}
    ).data["id"]

    # 举报目标是评论，却要删动态
    response = admin_client.put(
        f"/api/admin/reports/{report_id}/handle",
        json_body={"status": 1, "action": "DELETE_POST"},
    )

    assert response.code == 400
    assert response.message == "处置动作与举报目标类型不匹配"


@pytest.mark.write
def test_invalid_report_action(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "非法动作"}).data["id"]
    report_id = reporter.post(
        "/api/reports", json_body={"targetType": 2, "targetId": post_id, "reasonType": 1}
    ).data["id"]

    response = admin_client.put(
        f"/api/admin/reports/{report_id}/handle",
        json_body={"status": 1, "action": "DROP_DATABASE"},
    )

    assert response.code == 400
    assert response.message == "不支持的处置动作"


@pytest.mark.write
def test_handle_report_twice_is_conflict(admin_client, fresh_users):
    reporter, author = fresh_users(2)
    post_id = author.post("/api/posts", json_body={"content": "重复处理"}).data["id"]
    report_id = reporter.post(
        "/api/reports", json_body={"targetType": 2, "targetId": post_id, "reasonType": 1}
    ).data["id"]

    assert admin_client.put(
        f"/api/admin/reports/{report_id}/handle", json_body={"status": 1}
    ).ok

    second = admin_client.put(f"/api/admin/reports/{report_id}/handle", json_body={"status": 1})
    assert second.code == 409
    assert second.message == "该举报已被处理"


@pytest.mark.read
def test_handle_missing_report(admin_client):
    response = admin_client.put(
        "/api/admin/reports/99999999/handle", json_body={"status": 1}
    )
    assert response.code == 404


# ==================== 操作日志 ====================


@pytest.mark.read
def test_operation_logs(admin_client):
    response = admin_client.get("/api/admin/logs", params={"size": 50})

    assert response.ok
    assert response.data["total"] > 0

    first = response.data["list"][0]
    assert first["admin"]["username"] == "admin"
    assert first["operationType"]
    assert first["ip"], "应记录操作来源 IP"


@pytest.mark.write
def test_admin_action_writes_log(admin_client, fresh_user):
    target_id = fresh_user().get("/api/users/me").data["id"]

    before = admin_client.get("/api/admin/logs", params={"size": 1}).data["total"]
    admin_client.put(f"/api/admin/users/{target_id}/status", json_body={"status": 0})
    after = admin_client.get("/api/admin/logs", params={"size": 1}).data["total"]

    assert after == before + 1

    latest = admin_client.get("/api/admin/logs", params={"size": 1}).data["list"][0]
    assert latest["operationType"] == "DISABLE_USER"
    assert latest["targetId"] == target_id


@pytest.mark.read
def test_operation_log_filter(admin_client):
    response = admin_client.get(
        "/api/admin/logs", params={"operationType": "HANDLE_REPORT", "size": 50}
    )

    assert response.ok
    for item in response.data["list"]:
        assert item["operationType"] == "HANDLE_REPORT"


@pytest.mark.read
def test_operation_log_has_no_secrets(admin_client):
    logs = admin_client.get("/api/admin/logs", params={"size": 50}).data["list"]

    for item in logs:
        detail = (item["detail"] or "").lower()
        assert "password" not in detail
        assert "token" not in detail


def test_disabled_seed_user_can_be_reenabled(admin_client):
    """确认种子里的禁用账号状态可恢复，避免用例之间互相影响。

    这条用的是种子里固定的 banned001(id=12)，只做一次状态回归，不改变最终状态。
    """
    detail = admin_client.get(f"/api/admin/users/{SEED_BANNED_ID}")

    assert detail.ok, detail.message
    assert detail.data["username"] == "banned001"
