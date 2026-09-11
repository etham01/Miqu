package com.miqu.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 管理后台的动态与评论管理。 */
@DisplayName("管理后台 · 内容管理")
class AdminContentControllerTest extends BaseControllerTest {

    private String adminToken() throws Exception {
        return login("admin", "123456");
    }

    // ==================== 动态 ====================

    @Test
    @DisplayName("动态列表：共 40 条，带作者与图片")
    void listPosts() throws Exception {
        JsonNode json = exec(get("/api/admin/posts?size=50").header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(40);

        JsonNode post1 = null;
        for (JsonNode item : json.path("data").path("list")) {
            if (item.path("id").asText().equals("1")) {
                post1 = item;
            }
        }
        assertThat(post1).as("列表里应包含 post 1").isNotNull();
        assertThat(post1.path("images")).hasSize(9);
        assertThat(post1.path("author").path("username").asText()).isEqualTo("test001");
    }

    @Test
    @DisplayName("动态列表：按作者过滤")
    void listPosts_filterByUser() throws Exception {
        // test001(id=2) 发了 3 条
        JsonNode json = exec(get("/api/admin/posts?userId=2&size=50")
                .header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("动态列表：按内容关键词过滤，通配符被转义")
    void listPosts_filterByKeyword() throws Exception {
        String token = adminToken();

        assertThat(exec(get("/api/admin/posts").param("keyword", "青海湖")
                .header("Authorization", bearer(token))).path("data").path("total").asLong()).isEqualTo(1);

        assertThat(exec(get("/api/admin/posts").param("keyword", "%").param("size", "50")
                .header("Authorization", bearer(token))).path("data").path("total").asLong())
                .as("百分号必须被转义")
                .isZero();
    }

    @Test
    @DisplayName("管理员删除动态：级联清理并写入操作日志")
    void deletePost() throws Exception {
        String token = adminToken();

        assertThat(exec(delete("/api/admin/posts/1").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);

        // 动态已不可访问
        assertThat(exec(get("/api/posts/1")).path("code").asInt()).isEqualTo(404);

        // 日志已记录
        JsonNode latest = exec(get("/api/admin/logs").header("Authorization", bearer(token)))
                .path("data").path("list").get(0);
        assertThat(latest.path("operationType").asText()).isEqualTo("DELETE_POST");
        assertThat(latest.path("targetId").asText()).isEqualTo("1");
    }

    @Test
    @DisplayName("删除不存在的动态 → 404")
    void deletePost_notFound() throws Exception {
        mockMvc.perform(delete("/api/admin/posts/999999").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 评论 ====================

    @Test
    @DisplayName("评论列表：共 111 条")
    void listComments() throws Exception {
        JsonNode json = exec(get("/api/admin/comments?size=50").header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(111);
        assertThat(json.path("data").path("list").get(0).path("author").path("nickname").asText())
                .isNotBlank();
    }

    @Test
    @DisplayName("评论列表：按所属动态过滤（post 1 有 5 条）")
    void listComments_filterByPost() throws Exception {
        JsonNode json = exec(get("/api/admin/comments?postId=1&size=50")
                .header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("评论列表：按评论者过滤")
    void listComments_filterByUser() throws Exception {
        // user 3 在 post 1 下有 1 条评论，另有批量生成的若干条
        JsonNode json = exec(get("/api/admin/comments?userId=3&size=50")
                .header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isGreaterThan(0);
        for (JsonNode item : json.path("data").path("list")) {
            assertThat(item.path("author").path("id").asText()).isEqualTo("3");
        }
    }

    @Test
    @DisplayName("管理员删除评论：评论数回滚并写入操作日志")
    void deleteComment() throws Exception {
        String token = adminToken();

        long before = exec(get("/api/posts/1")).path("data").path("commentCount").asLong();

        assertThat(exec(delete("/api/admin/comments/1").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);

        assertThat(exec(get("/api/posts/1")).path("data").path("commentCount").asLong())
                .isEqualTo(before - 1);

        JsonNode latest = exec(get("/api/admin/logs").header("Authorization", bearer(token)))
                .path("data").path("list").get(0);
        assertThat(latest.path("operationType").asText()).isEqualTo("DELETE_COMMENT");
    }

    @Test
    @DisplayName("删除不存在的评论 → 404")
    void deleteComment_notFound() throws Exception {
        mockMvc.perform(delete("/api/admin/comments/999999").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 操作日志 ====================

    @Test
    @DisplayName("操作日志：种子有 4 条，按时间倒序")
    void listLogs() throws Exception {
        JsonNode json = exec(get("/api/admin/logs").header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(4);
        assertThat(json.path("data").path("list").get(0).path("admin").path("username").asText())
                .isEqualTo("admin");
    }

    @Test
    @DisplayName("操作日志：可按操作类型过滤")
    void listLogs_filterByType() throws Exception {
        JsonNode json = exec(get("/api/admin/logs?operationType=HANDLE_REPORT")
                .header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(2);
        for (JsonNode item : json.path("data").path("list")) {
            assertThat(item.path("operationType").asText()).isEqualTo("HANDLE_REPORT");
        }
    }

    @Test
    @DisplayName("操作日志：记录客户端 IP，且不含任何敏感字段")
    void listLogs_containsIpButNoSecrets() throws Exception {
        JsonNode first = exec(get("/api/admin/logs").header("Authorization", bearer(adminToken())))
                .path("data").path("list").get(0);

        assertThat(first.path("ip").asText()).isNotBlank();
        assertThat(first.path("detail").asText()).doesNotContain("password");
    }
}
