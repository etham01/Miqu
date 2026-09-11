package com.miqu.post;

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
 * 点赞模块接口测试。
 *
 * <p>用例自己在测试内创建动态，不依赖种子数据里的点赞分布——
 * 种子里的点赞是批量生成的，把它当断言依据会让用例变得难以理解。
 */
@DisplayName("点赞模块 /api/posts/{id}/like")
class PostLikeControllerTest extends BaseControllerTest {

    @Autowired
    private NotificationMapper notificationMapper;

    /** 由 test001 发一条动态，返回其 ID。 */
    private long createPostAsTest001() throws Exception {
        String token = login("test001", "123456");
        return exec(post("/api/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"用于点赞测试的动态\"}"))
                .path("data").path("id").asLong();
    }

    private long countNotifications(Long userId, Long actorId, Integer type) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getActorId, actorId)
                .eq(Notification::getType, type));
    }

    // ==================== 点赞 ====================

    @Test
    @DisplayName("点赞成功：点赞数 +1，likedByMe 变为 true")
    void like_success() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        JsonNode json = exec(post("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("liked").asBoolean()).isTrue();
        assertThat(json.path("data").path("likeCount").asInt()).isEqualTo(1);

        JsonNode detail = exec(get("/api/posts/" + postId)
                .header("Authorization", bearer(token))).path("data");
        assertThat(detail.path("likeCount").asInt()).isEqualTo(1);
        assertThat(detail.path("likedByMe").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("重复点赞 → 409")
    void like_duplicate() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        exec(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));

        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("已经点赞过该动态"));
    }

    @Test
    @DisplayName("点赞不存在的动态 → 404")
    void like_postNotFound() throws Exception {
        String token = login("test002", "123456");
        mockMvc.perform(post("/api/posts/999999/like").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("点赞：未登录 → 401")
    void like_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/posts/1/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("点赞他人动态会生成通知")
    void like_createsNotificationForAuthor() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        long before = countNotifications(2L, 3L, NotificationTypeEnum.LIKE.getCode());
        exec(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));

        assertThat(countNotifications(2L, 3L, NotificationTypeEnum.LIKE.getCode()))
                .as("作者 test001 应收到一条来自 test002 的点赞通知")
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("给自己的动态点赞不会产生通知")
    void like_ownPostDoesNotNotify() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test001", "123456");

        long before = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationTypeEnum.LIKE.getCode()));

        assertThat(exec(post("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);

        long after = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, NotificationTypeEnum.LIKE.getCode()));
        assertThat(after).isEqualTo(before);
    }

    // ==================== 取消点赞 ====================

    @Test
    @DisplayName("取消点赞成功：点赞数 -1，likedByMe 变为 false")
    void unlike_success() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        exec(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));

        JsonNode json = exec(delete("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("liked").asBoolean()).isFalse();
        assertThat(json.path("data").path("likeCount").asInt()).isZero();
    }

    @Test
    @DisplayName("取消未点赞的动态 → 404（不是静默成功）")
    void unlike_notLiked() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        mockMvc.perform(delete("/api/posts/" + postId + "/like").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("尚未点赞该动态"));
    }

    /**
     * 这是守护"关系表必须物理删除"这条设计决策的关键用例。
     *
     * <p>如果 post_like 沿用了 MyBatis-Plus 的逻辑删除，取消点赞会变成
     * {@code UPDATE post_like SET deleted=1}，唯一键 {@code uk_post_user} 仍被占住，
     * 第二次点赞会直接撞唯一键 —— 需求里"取消点赞后可以再次点赞"就不成立了。
     */
    @Test
    @DisplayName("取消点赞后可以再次点赞（唯一键已释放）")
    void unlike_thenLikeAgain_succeeds() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        assertThat(exec(post("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);
        assertThat(exec(delete("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);

        JsonNode again = exec(post("/api/posts/" + postId + "/like")
                .header("Authorization", bearer(token)));

        assertThat(again.path("code").asInt())
                .as("取消点赞后必须能再次点赞，否则说明 post_like 被逻辑删除了")
                .isEqualTo(200);
        assertThat(again.path("data").path("likeCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("反复点赞/取消点赞，计数始终与关系表一致")
    void likeToggle_keepsCountConsistent() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        for (int i = 0; i < 3; i++) {
            assertThat(exec(post("/api/posts/" + postId + "/like")
                    .header("Authorization", bearer(token))).path("data").path("likeCount").asInt())
                    .isEqualTo(1);
            assertThat(exec(delete("/api/posts/" + postId + "/like")
                    .header("Authorization", bearer(token))).path("data").path("likeCount").asInt())
                    .isZero();
        }
    }

    @Test
    @DisplayName("取消点赞会撤回对应的通知")
    void unlike_removesNotification() throws Exception {
        long postId = createPostAsTest001();
        String token = login("test002", "123456");

        exec(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));
        long afterLike = countNotifications(2L, 3L, NotificationTypeEnum.LIKE.getCode());

        exec(delete("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));

        assertThat(countNotifications(2L, 3L, NotificationTypeEnum.LIKE.getCode()))
                .isEqualTo(afterLike - 1);
    }

    @Test
    @DisplayName("多个用户点赞，计数累加正确")
    void like_multipleUsers() throws Exception {
        long postId = createPostAsTest001();

        for (String username : new String[]{"test002", "test003", "test004"}) {
            String token = login(username, "123456");
            exec(post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)));
        }

        assertThat(exec(get("/api/posts/" + postId)).path("data").path("likeCount").asInt())
                .isEqualTo(3);
        assertThat(exec(get("/api/posts/" + postId + "/likes")).path("data").path("total").asLong())
                .isEqualTo(3);
    }
}
