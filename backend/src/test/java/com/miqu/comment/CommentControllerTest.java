package com.miqu.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.common.enums.NotificationTypeEnum;
import com.miqu.entity.Notification;
import com.miqu.mapper.NotificationMapper;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论模块接口测试。
 *
 * <p>种子事实：{@code post} id=1 下恰好 5 条评论（id 1~5），
 * 分别由用户 3、5、7、9、11 发表；post 1 的作者是 test001(id=2)。
 */
@DisplayName("评论模块 /api/posts/{id}/comments")
class CommentControllerTest extends BaseControllerTest {

    private static final String POST_1 = "1";
    private static final String COMMENT_1 = "1";   // 由 test002(id=3) 发表

    @Autowired
    private NotificationMapper notificationMapper;

    private long commentCountOf(String postId) throws Exception {
        return exec(get("/api/posts/" + postId)).path("data").path("commentCount").asLong();
    }

    private long countCommentNotifications(Long userId, Long actorId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getActorId, actorId)
                .eq(Notification::getType, NotificationTypeEnum.COMMENT.getCode()));
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("评论列表：post 1 有 5 条评论")
    void list_postOne() throws Exception {
        JsonNode json = exec(get("/api/posts/" + POST_1 + "/comments"));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(5);
        assertThat(json.path("data").path("list")).hasSize(5);
    }

    @Test
    @DisplayName("评论列表：按时间正序返回（先发的在前）")
    void list_isOrderedAscending() throws Exception {
        JsonNode list = exec(get("/api/posts/" + POST_1 + "/comments")).path("data").path("list");

        assertThat(list.get(0).path("id").asText()).isEqualTo("1");

        String previous = null;
        for (JsonNode item : list) {
            String current = item.path("createTime").asText();
            if (previous != null) {
                assertThat(current.compareTo(previous)).isGreaterThanOrEqualTo(0);
            }
            previous = current;
        }
    }

    @Test
    @DisplayName("评论列表：游客可访问，mine 恒为 false")
    void list_asGuest() throws Exception {
        JsonNode list = exec(get("/api/posts/" + POST_1 + "/comments")).path("data").path("list");

        for (JsonNode item : list) {
            assertThat(item.path("mine").asBoolean()).isFalse();
        }
    }

    @Test
    @DisplayName("评论列表：登录用户看到自己的评论时 mine 为 true")
    void list_mineFlag() throws Exception {
        // test002(id=3) 发表了 comment 1
        String token = login("test002", "123456");
        JsonNode list = exec(get("/api/posts/" + POST_1 + "/comments")
                .header("Authorization", bearer(token))).path("data").path("list");

        assertThat(list.get(0).path("mine").asBoolean()).isTrue();
        assertThat(list.get(1).path("mine").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("评论列表：动态不存在 → 404")
    void list_postNotFound() throws Exception {
        mockMvc.perform(get("/api/posts/999999/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("评论列表：分页 size=2 返回 2 条且 hasNext")
    void list_pagination() throws Exception {
        JsonNode json = exec(get("/api/posts/" + POST_1 + "/comments?page=1&size=2"));

        assertThat(json.path("data").path("list")).hasSize(2);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(5);
        assertThat(json.path("data").path("hasNext").asBoolean()).isTrue();
    }

    // ==================== 发表 ====================

    @Test
    @DisplayName("发表评论成功，动态评论数 +1")
    void create_success() throws Exception {
        long before = commentCountOf(POST_1);
        String token = login("test002", "123456");
        String body = """
                {"content":"这是一条接口测试评论"}
                """;

        mockMvc.perform(post("/api/posts/" + POST_1 + "/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.postId").value(POST_1))
                .andExpect(jsonPath("$.data.content").value("这是一条接口测试评论"))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.author.username").value("test002"));

        assertThat(commentCountOf(POST_1)).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("发表评论：内容为空 → 400")
    void create_blankContent() throws Exception {
        String token = login("test002", "123456");

        mockMvc.perform(post("/api/posts/" + POST_1 + "/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("评论内容不能为空"));
    }

    @Test
    @DisplayName("发表评论：内容超过 500 字符 → 400")
    void create_contentTooLong() throws Exception {
        String token = login("test002", "123456");
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", "评".repeat(501)));

        mockMvc.perform(post("/api/posts/" + POST_1 + "/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("评论内容不能超过 500 个字符"));
    }

    @Test
    @DisplayName("发表评论：恰好 500 字符可以通过（边界值）")
    void create_contentExactlyMaxLength() throws Exception {
        String token = login("test002", "123456");
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", "评".repeat(500)));

        mockMvc.perform(post("/api/posts/" + POST_1 + "/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("发表评论：动态不存在 → 404")
    void create_postNotFound() throws Exception {
        String token = login("test002", "123456");

        mockMvc.perform(post("/api/posts/999999/comments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("发表评论：未登录 → 401")
    void create_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/posts/" + POST_1 + "/comments")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("评论他人动态会生成通知，且通知里带内容快照")
    void create_createsNotification() throws Exception {
        long before = countCommentNotifications(2L, 3L);
        String token = login("test002", "123456");

        exec(post("/api/posts/" + POST_1 + "/comments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"通知快照测试\"}"));

        assertThat(countCommentNotifications(2L, 3L)).isEqualTo(before + 1);

        Notification latest = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, 2L)
                .eq(Notification::getActorId, 3L)
                .eq(Notification::getType, NotificationTypeEnum.COMMENT.getCode())
                .orderByDesc(Notification::getId)
                .last("LIMIT 1"));
        assertThat(latest).isNotNull();
        // 内容快照的作用：即使动态被删，通知页也不会出现空白条目
        assertThat(latest.getContent()).isEqualTo("通知快照测试");
        assertThat(latest.getPostId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("评论自己的动态不会产生通知")
    void create_ownPostDoesNotNotify() throws Exception {
        // post 1 的作者是 test001
        String token = login("test001", "123456");
        long before = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationTypeEnum.COMMENT.getCode()));

        assertThat(exec(post("/api/posts/" + POST_1 + "/comments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"自己评论自己\"}")).path("code").asInt()).isEqualTo(200);

        long after = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationTypeEnum.COMMENT.getCode()));
        assertThat(after).isEqualTo(before);
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除自己的评论成功，动态评论数 -1")
    void delete_ownComment() throws Exception {
        long before = commentCountOf(POST_1);
        // comment 1 由 test002(id=3) 发表
        String token = login("test002", "123456");

        JsonNode json = exec(delete("/api/comments/" + COMMENT_1)
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(commentCountOf(POST_1)).isEqualTo(before - 1);
        // 已删除的评论不再出现在列表里
        assertThat(exec(get("/api/posts/" + POST_1 + "/comments")).path("data").path("total").asLong())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("删除他人的评论 → 403")
    void delete_othersComment() throws Exception {
        // comment 1 的作者是 test002，用 test001 去删
        String token = login("test001", "123456");

        mockMvc.perform(delete("/api/comments/" + COMMENT_1).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限执行该操作"));
    }

    @Test
    @DisplayName("管理员可以删除任何人的评论")
    void delete_asAdmin() throws Exception {
        long before = commentCountOf(POST_1);
        String adminToken = login("admin", "123456");

        assertThat(exec(delete("/api/comments/" + COMMENT_1)
                .header("Authorization", bearer(adminToken))).path("code").asInt()).isEqualTo(200);
        assertThat(commentCountOf(POST_1)).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("重复删除同一条评论 → 404")
    void delete_twice() throws Exception {
        String token = login("test002", "123456");

        assertThat(exec(delete("/api/comments/" + COMMENT_1)
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);

        mockMvc.perform(delete("/api/comments/" + COMMENT_1).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("评论不存在或已被删除"));
    }

    @Test
    @DisplayName("删除不存在的评论 → 404")
    void delete_notFound() throws Exception {
        String token = login("test002", "123456");
        mockMvc.perform(delete("/api/comments/999999").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除评论：未登录 → 401")
    void delete_unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/comments/" + COMMENT_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
