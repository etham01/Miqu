package com.miqu.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 举报提交接口测试。
 *
 * <p>种子事实：post 1 的作者是 test001(id=2)，post 2 的作者是 test002(id=3)，
 * comment 1 的作者是 test002(id=3)。
 */
@DisplayName("举报模块 /api/reports")
class ReportControllerTest extends BaseControllerTest {

    private static final String REPORTS = "/api/reports";

    private JsonNode report(String token, int targetType, long targetId, int reasonType) throws Exception {
        return exec(post(REPORTS).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "targetType", targetType,
                        "targetId", targetId,
                        "reasonType", reasonType,
                        "reasonDetail", "接口测试提交的举报"))));
    }

    // ==================== 提交成功 ====================

    @Test
    @DisplayName("举报他人的动态成功")
    void reportPost_success() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = report(token, 2, 2, 1);

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("id").isMissingNode()).isFalse();
        assertThat(json.path("data").path("targetType").asInt()).isEqualTo(2);
        assertThat(json.path("data").path("targetId").asText()).isEqualTo("2");
    }

    @Test
    @DisplayName("举报他人的评论成功")
    void reportComment_success() throws Exception {
        // comment 1 由 test002 发表，test001 可以举报
        JsonNode json = report(login("test001", "123456"), 3, 1, 2);
        assertThat(json.path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("举报其他用户成功")
    void reportUser_success() throws Exception {
        JsonNode json = report(login("test001", "123456"), 1, 15, 2);
        assertThat(json.path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("被禁用的用户仍可作为举报对象（内容还在）")
    void reportBannedUser_success() throws Exception {
        JsonNode json = report(login("test001", "123456"), 1, 12, 1);
        assertThat(json.path("code").asInt()).isEqualTo(200);
    }

    // ==================== 业务规则 ====================

    @Test
    @DisplayName("不能举报自己的动态 → 400")
    void reportOwnPost() throws Exception {
        // post 1 是 test001 自己发的
        mockMvc.perform(post(REPORTS).header("Authorization", bearer(login("test001", "123456")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":2,\"targetId\":1,\"reasonType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能举报自己"));
    }

    @Test
    @DisplayName("不能举报自己 → 400")
    void reportSelf() throws Exception {
        mockMvc.perform(post(REPORTS).header("Authorization", bearer(login("test001", "123456")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":1,\"targetId\":2,\"reasonType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能举报自己"));
    }

    @Test
    @DisplayName("重复举报同一目标 → 409")
    void reportDuplicate() throws Exception {
        String token = login("test001", "123456");

        assertThat(report(token, 2, 2, 1).path("code").asInt()).isEqualTo(200);

        mockMvc.perform(post(REPORTS).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":2,\"targetId\":2,\"reasonType\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("你已经举报过该内容"));
    }

    @Test
    @DisplayName("举报目标不存在 → 404")
    void reportTargetNotFound() throws Exception {
        String token = login("test001", "123456");

        assertThat(report(token, 2, 999999, 1).path("code").asInt()).isEqualTo(404);
        assertThat(report(token, 1, 999999, 1).path("code").asInt()).isEqualTo(404);
        assertThat(report(token, 3, 999999, 1).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("举报已注销的用户 → 404")
    void reportDeletedUser() throws Exception {
        assertThat(report(login("test001", "123456"), 1, 13, 1).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("目标类型非法 → 400")
    void reportInvalidTargetType() throws Exception {
        // 4 是"举报"自身，不能作为被举报对象
        mockMvc.perform(post(REPORTS).header("Authorization", bearer(login("test001", "123456")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":4,\"targetId\":1,\"reasonType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("举报原因超出范围 → 400")
    void reportInvalidReasonType() throws Exception {
        mockMvc.perform(post(REPORTS).header("Authorization", bearer(login("test001", "123456")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":2,\"targetId\":2,\"reasonType\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("举报原因取值不合法"));
    }

    @Test
    @DisplayName("未登录举报 → 401")
    void reportUnauthenticated() throws Exception {
        mockMvc.perform(post(REPORTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":2,\"targetId\":2,\"reasonType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("举报后进入待处理队列，管理员能查到")
    void reportAppearsInAdminQueue() throws Exception {
        String userToken = login("test001", "123456");
        String adminToken = login("admin", "123456");

        long before = exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/admin/reports?status=0").header("Authorization", bearer(adminToken)))
                .path("data").path("total").asLong();

        report(userToken, 2, 2, 1);

        assertThat(exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/admin/reports?status=0").header("Authorization", bearer(adminToken)))
                .path("data").path("total").asLong()).isEqualTo(before + 1);
    }
}
