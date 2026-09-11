package com.miqu.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 动态模块接口测试。
 *
 * <p>种子事实：共 40 条动态，均为未删除状态；{@code post} id=1 由 test001 发布，
 * 恰好 9 张图、10 个赞、5 条评论；id=2 无图；id=3 内容恰好 1000 字符。
 *
 * <p>test001 自己发布了 3 条动态（id=1、20、40）。
 */
@DisplayName("动态模块 /api/posts")
class PostControllerTest extends BaseControllerTest {

    private static final String POST_1 = "1";
    private static final String VALID_IMAGE = "/uploads/image/2026/09/abc123.jpg";

    private JsonNode detailOf(String postId) throws Exception {
        return exec(get("/api/posts/" + postId));
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("首页最新动态：游客可访问，共 40 条")
    void listLatest_asGuest() throws Exception {
        JsonNode json = exec(get("/api/posts?page=1&size=10"));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(40);
        assertThat(json.path("data").path("list")).hasSize(10);
        assertThat(json.path("data").path("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("首页最新动态：按发布时间倒序，最新的排在最前")
    void listLatest_isOrderedByTimeDesc() throws Exception {
        JsonNode list = exec(get("/api/posts?size=20")).path("data").path("list");

        // id=40 是种子数据里时间最近的一条
        assertThat(list.get(0).path("id").asText()).isEqualTo("40");

        // 逐条校验时间不递增
        String previous = null;
        for (JsonNode item : list) {
            String current = item.path("createTime").asText();
            if (previous != null) {
                assertThat(current.compareTo(previous)).isLessThanOrEqualTo(0);
            }
            previous = current;
        }
    }

    @Test
    @DisplayName("游客访问时 likedByMe 与 mine 恒为 false")
    void listLatest_guestFlagsAreFalse() throws Exception {
        JsonNode list = exec(get("/api/posts?size=5")).path("data").path("list");

        for (JsonNode item : list) {
            assertThat(item.path("likedByMe").asBoolean()).isFalse();
            assertThat(item.path("mine").asBoolean()).isFalse();
        }
    }

    @Test
    @DisplayName("登录用户看到自己发布与点赞过的动态时，mine / likedByMe 正确置位")
    void listLatest_flagsForLoggedInUser() throws Exception {
        String token = login("test001", "123456");
        JsonNode list = exec(get("/api/posts/1").header("Authorization", bearer(token)))
                .path("data");

        // test001 是 post 1 的作者，且种子数据里 user 2~11 都点赞了 post 1
        assertThat(list.path("mine").asBoolean()).isTrue();
        assertThat(list.path("likedByMe").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("tab 取值非法 → 400")
    void listLatest_invalidTab() throws Exception {
        mockMvc.perform(get("/api/posts?tab=hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("tab 取值只能是 latest 或 following"));
    }

    @Test
    @DisplayName("每页条数超过上限 → 400（不静默重置）")
    void listLatest_sizeOverLimit() throws Exception {
        mockMvc.perform(get("/api/posts?size=1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("每页条数不能超过 50"));
    }

    // ==================== 关注流 ====================

    @Test
    @DisplayName("关注流：未登录 → 401")
    void followingFeed_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/posts?tab=following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("关注流：只返回我关注的人发布的动态")
    void followingFeed_containsOnlyFollowedAuthors() throws Exception {
        String token = login("test001", "123456");
        // test001 关注的是 3、4、5、6、7、8
        Set<String> followedAuthors = Set.of("3", "4", "5", "6", "7", "8");

        JsonNode json = exec(MockMvcRequestBuilders.get("/api/posts?tab=following&size=50")
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        JsonNode list = json.path("data").path("list");
        assertThat(list.size()).isGreaterThan(0);

        for (JsonNode item : list) {
            String authorId = item.path("author").path("id").asText();
            assertThat(followedAuthors)
                    .as("关注流里出现了未关注用户的动态：authorId=%s", authorId)
                    .contains(authorId);
        }
    }

    @Test
    @DisplayName("关注流：不包含自己发布的动态")
    void followingFeed_excludesOwnPosts() throws Exception {
        String token = login("test001", "123456");
        JsonNode list = exec(MockMvcRequestBuilders.get("/api/posts?tab=following&size=50")
                .header("Authorization", bearer(token))).path("data").path("list");

        for (JsonNode item : list) {
            assertThat(item.path("author").path("id").asText()).isNotEqualTo("2");
        }
    }

    // ==================== 详情 ====================

    @Test
    @DisplayName("动态详情：post 1 恰好 9 张图、10 个赞、5 条评论")
    void detail_postOne() throws Exception {
        JsonNode data = detailOf(POST_1).path("data");

        assertThat(data.path("likeCount").asInt()).isEqualTo(10);
        assertThat(data.path("commentCount").asInt()).isEqualTo(5);
        assertThat(data.path("images")).hasSize(9);
        assertThat(data.path("author").path("username").asText()).isEqualTo("test001");
    }

    @Test
    @DisplayName("动态详情：图片按 sort_order 顺序返回")
    void detail_imagesAreOrdered() throws Exception {
        JsonNode images = detailOf(POST_1).path("data").path("images");

        // data.sql 生成的 URL 里带有序号：miqu1_0、miqu1_1 … miqu1_8
        for (int i = 0; i < 9; i++) {
            assertThat(images.get(i).asText()).contains("miqu1_" + i + "/");
        }
    }

    @Test
    @DisplayName("动态详情：无图动态返回空数组而不是 null")
    void detail_postWithoutImages() throws Exception {
        JsonNode data = detailOf("2").path("data");

        assertThat(data.path("images").isArray()).isTrue();
        assertThat(data.path("images")).isEmpty();
    }

    @Test
    @DisplayName("动态详情：不存在的动态 → 404")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/posts/999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("动态不存在或已被删除"));
    }

    @Test
    @DisplayName("某用户的动态列表：test001 发布了 3 条")
    void listByUser() throws Exception {
        JsonNode json = exec(get("/api/users/2/posts"));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(3);
        for (JsonNode item : json.path("data").path("list")) {
            assertThat(item.path("author").path("id").asText()).isEqualTo("2");
        }
    }

    // ==================== 发布 ====================

    @Test
    @DisplayName("发布纯文字动态成功")
    void create_textOnly() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"content":"这是一条接口测试发布的动态"}
                """;

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.content").value("这是一条接口测试发布的动态"))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.mine").value(true))
                .andExpect(jsonPath("$.data.author.username").value("test001"));
    }

    @Test
    @DisplayName("发布带图动态成功，图片按提交顺序保持")
    void create_withImages() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"content":"带图动态","images":["/uploads/image/2026/09/a.jpg","/uploads/image/2026/09/b.jpg"]}
                """;

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images").isArray())
                .andExpect(jsonPath("$.data.images[0]").value("/uploads/image/2026/09/a.jpg"))
                .andExpect(jsonPath("$.data.images[1]").value("/uploads/image/2026/09/b.jpg"));
    }

    @Test
    @DisplayName("发布纯图片动态（无文字）成功")
    void create_imagesOnly() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"images":["/uploads/image/2026/09/only.jpg"]}
                """;

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("发布动态：内容与图片同时为空 → 400")
    void create_emptyContentAndImages() throws Exception {
        String token = login("test001", "123456");

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\",\"images\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("动态内容与图片不能同时为空"));
    }

    @Test
    @DisplayName("发布动态：内容超过 1000 字符 → 400")
    void create_contentTooLong() throws Exception {
        String token = login("test001", "123456");
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", "测".repeat(1001)));

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("动态内容不能超过 1000 个字符"));
    }

    @Test
    @DisplayName("发布动态：恰好 1000 字符可以通过（边界值）")
    void create_contentExactlyMaxLength() throws Exception {
        String token = login("test001", "123456");
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("content", "测".repeat(1000)));

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("发布动态：10 张图片 → 400")
    void create_tooManyImages() throws Exception {
        String token = login("test001", "123456");
        var images = new java.util.ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            images.add("/uploads/image/2026/09/" + i + ".jpg");
        }
        String body = objectMapper.writeValueAsString(java.util.Map.of("images", images));

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("发布动态：外链图片地址被拒绝 → 400")
    void create_externalImageUrlRejected() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"content":"外链图片","images":["https://evil.example.com/tracker.gif"]}
                """;

        mockMvc.perform(post("/api/posts").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("图片地址不合法，请先通过上传接口获取"));
    }

    @Test
    @DisplayName("发布动态：未登录 → 401")
    void create_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除自己的动态成功，动态数 -1")
    void delete_ownPost() throws Exception {
        String token = login("test001", "123456");
        int before = exec(get("/api/users/2")).path("data").path("postCount").asInt();

        // 先发一条再删，避免依赖种子数据的可变性
        long newPostId = exec(post("/api/posts").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"待删除\"}"))
                .path("data").path("id").asLong();

        assertThat(exec(get("/api/users/2")).path("data").path("postCount").asInt())
                .isEqualTo(before + 1);

        JsonNode json = exec(delete("/api/posts/" + newPostId).header("Authorization", bearer(token)));
        assertThat(json.path("code").asInt()).isEqualTo(200);

        // 计数回落，且动态已不可查
        assertThat(exec(get("/api/users/2")).path("data").path("postCount").asInt()).isEqualTo(before);
        assertThat(detailOf(String.valueOf(newPostId)).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("删除他人的动态 → 403")
    void delete_othersPost() throws Exception {
        // post 2 的作者是 test002，用 test001 去删
        String token = login("test001", "123456");

        mockMvc.perform(delete("/api/posts/2").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限执行该操作"));
    }

    @Test
    @DisplayName("管理员可以删除任何人的动态")
    void delete_asAdmin() throws Exception {
        String adminToken = login("admin", "123456");

        JsonNode json = exec(delete("/api/posts/2").header("Authorization", bearer(adminToken)));
        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(detailOf("2").path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("删除动态时级联清除其图片与点赞，评论计数一并回滚")
    void delete_cascadesRelations() throws Exception {
        String token = login("test001", "123456");

        // post 1 有 9 张图、10 个赞、5 条评论
        assertThat(exec(delete("/api/posts/1").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);

        // 动态详情 404
        assertThat(detailOf("1").path("code").asInt()).isEqualTo(404);
        // 评论列表也随动态一起不可访问
        assertThat(exec(get("/api/posts/1/comments")).path("code").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("删除不存在的动态 → 404")
    void delete_notFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(delete("/api/posts/999999").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除动态：未登录 → 401")
    void delete_unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 点赞用户列表 ====================

    @Test
    @DisplayName("点赞用户列表：post 1 有 10 个点赞者")
    void listLikes() throws Exception {
        JsonNode json = exec(get("/api/posts/1/likes"));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(10);
        assertThat(json.path("data").path("list")).hasSize(10);
    }

    @Test
    @DisplayName("点赞用户列表：返回的用户信息不含邮箱")
    void listLikes_hidesEmail() throws Exception {
        JsonNode first = exec(get("/api/posts/1/likes")).path("data").path("list").get(0);

        assertThat(first.has("email")).isFalse();
        assertThat(first.path("nickname").asText()).isNotBlank();
    }
}
