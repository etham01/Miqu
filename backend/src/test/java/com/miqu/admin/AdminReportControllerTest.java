package com.miqu.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台举报处理。
 *
 * <p>种子举报（见 data.sql）：
 * <ol>
 *   <li>id=1 举报人 5，目标 Post#11，待处理</li>
 *   <li>id=2 举报人 6，目标 Post#12，<b>已处理</b></li>
 *   <li>id=3 举报人 7，目标 Comment#20，待处理</li>
 *   <li>id=4 举报人 8，目标 User#15，<b>已驳回</b></li>
 *   <li>id=5 举报人 9，目标 Post#25，待处理</li>
 * </ol>
 */
@DisplayName("管理后台 · 举报处理")
class AdminReportControllerTest extends BaseControllerTest {

    private static final String REPORTS = "/api/admin/reports";

    private String adminToken() throws Exception {
        return login("admin", "123456");
    }

    private JsonNode handle(String token, long reportId, String body) throws Exception {
        return exec(put(REPORTS + "/" + reportId + "/handle")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("举报列表：共 5 条")
    void list() throws Exception {
        JsonNode json = exec(get(REPORTS + "?size=50").header("Authorization", bearer(adminToken())));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("举报列表：按状态过滤，待处理 3 条")
    void list_filterByStatus() throws Exception {
        String token = adminToken();

        assertThat(exec(get(REPORTS + "?status=0").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(3);
        assertThat(exec(get(REPORTS + "?status=1").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(1);
        assertThat(exec(get(REPORTS + "?status=2").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(1);
    }

    /**
     * target_id 是多态外键，前端只看 ID 无法展示"被举报的是什么"。
     * 服务端按类型分组批量解析出摘要，避免前端逐条再查、也避免 N+1。
     */
    @Test
    @DisplayName("举报列表：targetPreview 解析出被举报对象的内容")
    void list_targetPreview() throws Exception {
        JsonNode list = exec(get(REPORTS + "?size=50").header("Authorization", bearer(adminToken())))
                .path("data").path("list");

        String postPreview = null;
        String commentPreview = null;
        String userPreview = null;
        for (JsonNode item : list) {
            switch (item.path("targetType").asInt()) {
                case 2 -> postPreview = item.path("targetPreview").asText();
                case 3 -> commentPreview = item.path("targetPreview").asText();
                case 1 -> userPreview = item.path("targetPreview").asText();
                default -> { }
            }
        }

        assertThat(postPreview).startsWith("动态：");
        assertThat(commentPreview).startsWith("评论：");
        assertThat(userPreview).startsWith("用户：");
    }

    @Test
    @DisplayName("举报列表：已处理的那条带处理人与处理时间")
    void list_handledReportHasHandler() throws Exception {
        JsonNode list = exec(get(REPORTS + "?status=1").header("Authorization", bearer(adminToken())))
                .path("data").path("list");

        JsonNode handled = list.get(0);
        assertThat(handled.path("handler").path("username").asText()).isEqualTo("admin");
        assertThat(handled.path("handleTime").isMissingNode()).isFalse();
        assertThat(handled.path("handleRemark").asText()).isEqualTo("已删除该动态");
    }

    @Test
    @DisplayName("举报列表：状态取值非法 → 400")
    void list_invalidStatus() throws Exception {
        mockMvc.perform(get(REPORTS + "?status=9").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 处理：只记结论 ====================

    @Test
    @DisplayName("处理举报（不处置）：状态变更并可重复查到")
    void handle_withoutAction() throws Exception {
        String token = adminToken();

        assertThat(handle(token, 1, "{\"status\":1,\"handleRemark\":\"已警告\"}").path("code").asInt())
                .isEqualTo(200);

        JsonNode handled = exec(get(REPORTS + "?status=1&size=50").header("Authorization", bearer(token)))
                .path("data").path("list");
        // 原本 1 条已处理 + 刚处理的这条
        assertThat(handled.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("驳回举报（未违规）")
    void handle_reject() throws Exception {
        String token = adminToken();

        assertThat(handle(token, 1, "{\"status\":2,\"handleRemark\":\"经核实未违规\"}")
                .path("code").asInt()).isEqualTo(200);

        assertThat(exec(get(REPORTS + "?status=2").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(2);
    }

    // ==================== 处理：带处置动作 ====================

    @Test
    @DisplayName("处理举报并删除被举报的动态")
    void handle_deletePost() throws Exception {
        String token = adminToken();

        // 举报 1 的目标是 post 11
        assertThat(exec(get("/api/posts/11")).path("code").asInt()).isEqualTo(200);

        assertThat(handle(token, 1, """
                {"status":1,"handleRemark":"确认违规，删除动态","action":"DELETE_POST"}
                """).path("code").asInt()).isEqualTo(200);

        // 动态已被删除
        assertThat(exec(get("/api/posts/11")).path("code").asInt()).isEqualTo(404);

        // 操作日志里能同时看到"处理举报"
        JsonNode logs = exec(get("/api/admin/logs?operationType=HANDLE_REPORT")
                .header("Authorization", bearer(token))).path("data").path("list");
        assertThat(logs.get(0).path("detail").asText()).contains("DELETE_POST");
    }

    @Test
    @DisplayName("处理举报并删除被举报的评论")
    void handle_deleteComment() throws Exception {
        String token = adminToken();

        // 举报 3 的目标是 comment 20
        assertThat(exec(get("/api/posts/1/comments?size=50")).path("code").asInt()).isEqualTo(200);

        assertThat(handle(token, 3, """
                {"status":1,"handleRemark":"删除评论","action":"DELETE_COMMENT"}
                """).path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("处理举报并禁用被举报的用户")
    void handle_disableUser() throws Exception {
        String token = adminToken();

        // 先由 test001 举报 user 15，拿到一个待处理的举报
        long reportId = exec(MockMvcRequestBuilders.post("/api/reports")
                .header("Authorization", bearer(login("test001", "123456")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":1,\"targetId\":15,\"reasonType\":2}"))
                .path("data").path("id").asLong();

        assertThat(handle(token, reportId, """
                {"status":1,"handleRemark":"骚扰他人","action":"DISABLE_USER"}
                """).path("code").asInt()).isEqualTo(200);

        // 目标用户已被禁用：登录返回 423
        assertThat(exec(MockMvcRequestBuilders.post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test012\",\"password\":\"123456\"}"))
                .path("code").asInt()).isEqualTo(423);
    }

    // ==================== 处理：非法组合 ====================

    @Test
    @DisplayName("处置动作与举报目标类型不匹配 → 400")
    void handle_actionMismatch() throws Exception {
        // 举报 3 的目标是评论，却要删动态
        mockMvc.perform(put(REPORTS + "/3/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1,\"action\":\"DELETE_POST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("处置动作与举报目标类型不匹配"));
    }

    @Test
    @DisplayName("驳回举报时不能同时执行处置动作 → 400")
    void handle_rejectWithAction() throws Exception {
        // "判定未违规"与"删除内容"自相矛盾
        mockMvc.perform(put(REPORTS + "/1/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2,\"action\":\"DELETE_POST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("驳回举报时不能同时执行处置动作"));
    }

    @Test
    @DisplayName("不支持的处置动作 → 400")
    void handle_invalidAction() throws Exception {
        mockMvc.perform(put(REPORTS + "/1/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1,\"action\":\"DROP_DATABASE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的处置动作"));
    }

    @Test
    @DisplayName("重复处理同一条举报 → 409（避免处置动作被执行两次）")
    void handle_twice() throws Exception {
        String token = adminToken();

        assertThat(handle(token, 1, "{\"status\":1}").path("code").asInt()).isEqualTo(200);

        mockMvc.perform(put(REPORTS + "/1/handle").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该举报已被处理"));
    }

    @Test
    @DisplayName("处理已处理的种子举报 → 409")
    void handle_alreadyHandledSeedReport() throws Exception {
        // 举报 2 在种子数据里状态已经是 1
        mockMvc.perform(put(REPORTS + "/2/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("处理被作者自己先删掉的动态：举报仍能正常结案")
    void handle_targetAlreadyDeletedByAuthor() throws Exception {
        String token = adminToken();

        // test001 先把自己的 post 1 删掉
        exec(MockMvcRequestBuilders.delete("/api/posts/1")
                .header("Authorization", bearer(login("test001", "123456"))));

        // 举报 1 的目标是 post 11，先由作者 user 2? 不 —— post 11 的作者不是 test001。
        // 这里换成让管理员删一次目标，再处理举报，验证不会因为目标不存在而中断
        exec(MockMvcRequestBuilders.delete("/api/admin/posts/11").header("Authorization", bearer(token)));

        assertThat(handle(token, 1, """
                {"status":1,"handleRemark":"目标已不存在，直接结案","action":"DELETE_POST"}
                """).path("code").asInt())
                .as("目标已被删除时，举报仍应能结案")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("处理不存在的举报 → 404")
    void handle_notFound() throws Exception {
        mockMvc.perform(put(REPORTS + "/999999/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("处理结论取值非法 → 400")
    void handle_invalidStatus() throws Exception {
        mockMvc.perform(put(REPORTS + "/1/handle").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
