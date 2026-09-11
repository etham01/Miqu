package com.miqu.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 管理后台数据统计。
 *
 * <p>种子事实：21 用户 / 40 动态 / 111 评论 / 3 条待处理举报。
 * 种子数据的时间都落在过去，因此"今日新增"应当全为 0。
 */
@DisplayName("管理后台 · 数据统计")
class AdminStatsControllerTest extends BaseControllerTest {

    @Test
    @DisplayName("统计总数与种子数据一致（已注销用户不计入）")
    void stats_totals() throws Exception {
        JsonNode data = exec(get("/api/admin/stats")
                .header("Authorization", bearer(login("admin", "123456")))).path("data");

        // 表里共 21 行，但 deleted001 是逻辑删除，统计口径只算存活的 20 个
        assertThat(data.path("userTotal").asLong()).isEqualTo(20);
        assertThat(data.path("postTotal").asLong()).isEqualTo(40);
        assertThat(data.path("commentTotal").asLong()).isEqualTo(111);
    }

    @Test
    @DisplayName("待处理举报数用于后台首页待办提醒")
    void stats_pendingReports() throws Exception {
        JsonNode data = exec(get("/api/admin/stats")
                .header("Authorization", bearer(login("admin", "123456")))).path("data");

        assertThat(data.path("pendingReportTotal").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("今日新增用户与动态为 0（种子数据都在更早）")
    void stats_todayNew() throws Exception {
        JsonNode data = exec(get("/api/admin/stats")
                .header("Authorization", bearer(login("admin", "123456")))).path("data");

        // 用户注册时间在 15~120 天前；动态最早也在 40 小时前，都跨过了今天
        assertThat(data.path("todayNewUser").asLong()).isZero();
        assertThat(data.path("todayNewPost").asLong()).isZero();
    }

    /**
     * 评论不能断言为 0：{@code data.sql} 里评论的时间是
     * {@code NOW - (postId*2 + userId)} **小时**，偏移量小于当前小时数的那些
     * 正好落在今天。断言成一个固定值会让用例随执行时刻而飘。
     */
    @Test
    @DisplayName("今日新增评论落在 [0, 评论总数] 区间内")
    void stats_todayNewComment() throws Exception {
        JsonNode data = exec(get("/api/admin/stats")
                .header("Authorization", bearer(login("admin", "123456")))).path("data");

        long commentTotal = data.path("commentTotal").asLong();
        assertThat(data.path("todayNewComment").asLong())
                .isBetween(0L, commentTotal);
    }

    @Test
    @DisplayName("新发表一条评论后，今日新增评论 +1")
    void stats_todayNewCommentIncrements() throws Exception {
        String adminToken = login("admin", "123456");

        long before = exec(get("/api/admin/stats").header("Authorization", bearer(adminToken)))
                .path("data").path("todayNewComment").asLong();

        exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/posts/1/comments")
                .header("Authorization", bearer(login("test002", "123456")))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"content\":\"用于统计测试的评论\"}"));

        assertThat(exec(get("/api/admin/stats").header("Authorization", bearer(adminToken)))
                .path("data").path("todayNewComment").asLong()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("新注册用户后，今日新增用户数 +1")
    void stats_todayNewUserIncrements() throws Exception {
        String token = login("admin", "123456");
        long before = exec(get("/api/admin/stats").header("Authorization", bearer(token)))
                .path("data").path("todayNewUser").asLong();

        exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"statstest","password":"123456","nickname":"统计测试","email":"statstest@miqu.com"}
                        """));

        assertThat(exec(get("/api/admin/stats").header("Authorization", bearer(token)))
                .path("data").path("todayNewUser").asLong()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("统计字段全部是数字，不是字符串")
    void stats_fieldsAreNumbers() throws Exception {
        JsonNode data = exec(get("/api/admin/stats")
                .header("Authorization", bearer(login("admin", "123456")))).path("data");

        assertThat(data.path("userTotal").isNumber()).isTrue();
        assertThat(data.path("postTotal").isNumber()).isTrue();
        assertThat(data.path("pendingReportTotal").isNumber()).isTrue();
    }

    @Test
    @DisplayName("删除动态后总数下降")
    void stats_postTotalDecreasesAfterDelete() throws Exception {
        String token = login("admin", "123456");

        exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/posts/3").header("Authorization", bearer(token)));

        assertThat(exec(get("/api/admin/stats").header("Authorization", bearer(token)))
                .path("data").path("postTotal").asLong()).isEqualTo(39);
    }

    @Test
    @DisplayName("待处理举报数会随处理而下降")
    void stats_pendingReportDecreasesAfterHandling() throws Exception {
        String token = login("admin", "123456");

        exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/admin/reports/1/handle").header("Authorization", bearer(token))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"status\":1}"));

        assertThat(exec(get("/api/admin/stats").header("Authorization", bearer(token)))
                .path("data").path("pendingReportTotal").asLong()).isEqualTo(2);
    }
}
