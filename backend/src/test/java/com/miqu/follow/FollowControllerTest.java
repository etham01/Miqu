package com.miqu.follow;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关注模块接口测试。
 *
 * <p>种子关系（来自 data.sql，结尾已重算过计数，可直接依赖）：
 * <ul>
 *   <li>{@code test001}(id=2) 关注 3、4、5、6、7、8 —— 关注数 6</li>
 *   <li>{@code test001}(id=2) 的粉丝 14 人</li>
 *   <li>{@code test001}(id=2) 与 {@code test002}(id=3) <b>互相关注</b></li>
 *   <li>id=12 已禁用、id=13 已注销</li>
 * </ul>
 *
 * <p>计数断言采用"变化前后差值"而不是写死的绝对值：
 * 这样即使将来调整种子数据规模，用例也不会失效。
 */
@DisplayName("关注模块 /api/users/{id}/follow")
class FollowControllerTest extends BaseControllerTest {

    private static final String USER_2 = "2";   // test001，主测试账号
    private static final String USER_3 = "3";   // test002，与 test001 互相关注
    private static final long BANNED_USER_ID = 12L;
    private static final long DELETED_USER_ID = 13L;
    private static final long NOT_FOUND_USER_ID = 999999L;

    private JsonNode profileOf(String userId) throws Exception {
        return exec(get("/api/users/" + userId));
    }

    // ==================== 关注 ====================

    @Test
    @DisplayName("关注成功：粉丝数 +1，返回 following=true")
    void follow_success() throws Exception {
        // 9 是 test001 尚未关注的用户
        int before = profileOf("9").path("data").path("followerCount").asInt();

        String token = login("test001", "123456");
        JsonNode json = exec(post("/api/users/9/follow").header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("following").asBoolean()).isTrue();
        assertThat(json.path("data").path("followerCount").asInt()).isEqualTo(before + 1);

        // 再次查询主页确认计数已落库
        assertThat(profileOf("9").path("data").path("followerCount").asInt()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("关注成功：自己的关注数 +1")
    void follow_incrementsOwnFollowingCount() throws Exception {
        int before = profileOf(USER_2).path("data").path("followingCount").asInt();

        String token = login("test001", "123456");
        exec(post("/api/users/9/follow").header("Authorization", bearer(token)));

        int after = profileOf(USER_2).path("data").path("followingCount").asInt();
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("重复关注 → 409")
    void follow_duplicate() throws Exception {
        // test001 已经关注了 test002
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/users/" + USER_3 + "/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("已经关注过该用户"));
    }

    @Test
    @DisplayName("关注自己 → 400")
    void follow_self() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/users/" + USER_2 + "/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能关注自己"));
    }

    @Test
    @DisplayName("关注被禁用的用户 → 423")
    void follow_bannedUser() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/users/" + BANNED_USER_ID + "/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(423));
    }

    @Test
    @DisplayName("关注不存在的用户 → 404")
    void follow_nonexistentUser() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/users/" + NOT_FOUND_USER_ID + "/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("关注已注销的用户 → 404（逻辑删除视为不存在）")
    void follow_deletedUser() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/users/" + DELETED_USER_ID + "/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("未登录关注 → 401")
    void follow_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/users/9/follow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 取消关注 ====================

    @Test
    @DisplayName("取消关注成功：粉丝数 -1，返回 following=false")
    void unfollow_success() throws Exception {
        // test001 已关注 4
        int before = profileOf("4").path("data").path("followerCount").asInt();

        String token = login("test001", "123456");
        JsonNode json = exec(delete("/api/users/4/follow").header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("following").asBoolean()).isFalse();
        assertThat(json.path("data").path("followerCount").asInt()).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("取消未关注的关系 → 404（不是静默成功）")
    void unfollow_notFollowing() throws Exception {
        // test001 没有关注 9
        String token = login("test001", "123456");
        mockMvc.perform(delete("/api/users/9/follow").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("尚未关注该用户"));
    }

    @Test
    @DisplayName("取消关注后可以再次关注（关系表是物理删除，唯一键已释放）")
    void unfollow_thenFollowAgain_succeeds() throws Exception {
        String token = login("test001", "123456");

        // 取消
        assertThat(exec(delete("/api/users/4/follow").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);

        // 重新关注必须成功。如果 follow 表用了逻辑删除，
        // uk_follower_following 会被已软删的行占住，这里会变成 500 或 409
        JsonNode again = exec(post("/api/users/4/follow").header("Authorization", bearer(token)));
        assertThat(again.path("code").asInt()).isEqualTo(200);
        assertThat(again.path("data").path("following").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("未登录取消关注 → 401")
    void unfollow_unauthenticated() throws Exception {
        mockMvc.perform(delete("/api/users/4/follow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("我的关注列表：test001 关注了 6 个人")
    void myFollowing() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(MockMvcRequestBuilders.get("/api/users/me/following")
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(6);
        assertThat(json.path("data").path("list")).hasSize(6);
        // 自己关注的人，followedByMe 必然为 true
        for (JsonNode item : json.path("data").path("list")) {
            assertThat(item.path("followedByMe").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("我的粉丝列表：test001 有 14 个粉丝，分页默认每页 10 条")
    void myFollowers() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(MockMvcRequestBuilders.get("/api/users/me/followers")
                .header("Authorization", bearer(token)));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(14);
        // PageQuery 默认 size=10，因此首屏最多返回 10 条
        assertThat(json.path("data").path("list")).hasSize(10);
        assertThat(json.path("data").path("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("我的粉丝列表：size=50 时返回全部 14 条")
    void myFollowers_allInOnePage() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(MockMvcRequestBuilders.get("/api/users/me/followers?size=50")
                .header("Authorization", bearer(token)));

        assertThat(json.path("data").path("total").asLong()).isEqualTo(14);
        assertThat(json.path("data").path("list")).hasSize(14);
        assertThat(json.path("data").path("hasNext").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("粉丝列表的 followedByMe 反映我是否关注了对方（回关按钮的依据）")
    void myFollowers_followedByMeFlag() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(MockMvcRequestBuilders.get("/api/users/me/followers?size=50")
                .header("Authorization", bearer(token)));

        // test001 的粉丝是 3、4、5、6、7、8 以及 14~21；
        // 其中 3~8 被 test001 回关了，14~21 没有
        int followedBack = 0;
        int notFollowedBack = 0;
        for (JsonNode item : json.path("data").path("list")) {
            if (item.path("followedByMe").asBoolean()) {
                followedBack++;
            } else {
                notFollowedBack++;
            }
        }
        assertThat(followedBack).isEqualTo(6);
        assertThat(notFollowedBack).isEqualTo(8);
    }

    @Test
    @DisplayName("查看他人关注列表：游客可访问，followedByMe 恒为 false")
    void othersFollowing_asGuest() throws Exception {
        mockMvc.perform(get("/api/users/" + USER_2 + "/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.list[0].followedByMe").value(false));
    }

    @Test
    @DisplayName("关注列表分页：size=2 时返回 2 条且 hasNext=true")
    void following_pagination() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(MockMvcRequestBuilders.get("/api/users/me/following?page=1&size=2")
                .header("Authorization", bearer(token)));

        assertThat(json.path("data").path("list")).hasSize(2);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(6);
        assertThat(json.path("data").path("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("不存在的用户 → 关注列表返回 404")
    void following_userNotFound() throws Exception {
        mockMvc.perform(get("/api/users/" + NOT_FOUND_USER_ID + "/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 用户主页的关注状态 ====================

    @Test
    @DisplayName("主页关注状态：test001 看 test002 是互相关注")
    void profile_mutualFollow() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get("/api/users/" + USER_3).header("Authorization", bearer(token)));

        JsonNode data = json.path("data");
        assertThat(data.path("followedByMe").asBoolean()).isTrue();
        assertThat(data.path("followingMe").asBoolean()).isTrue();
        assertThat(data.path("mutual").asBoolean()).isTrue();
        assertThat(data.path("username").asText()).isEqualTo("test002");
        // 公开主页不应包含邮箱
        assertThat(data.has("email")).isFalse();
    }

    @Test
    @DisplayName("主页关注状态：单向关注时 mutual 为 false")
    void profile_oneWayFollow() throws Exception {
        // 种子数据里 test001 关注的人（3~8）全都回关了他，找不到天然的单向关系，
        // 因此这里自己建立一条：test001 关注 9（user 11 并未关注 test001）
        String token = login("test001", "123456");
        exec(post("/api/users/9/follow").header("Authorization", bearer(token)));

        JsonNode data = exec(get("/api/users/9").header("Authorization", bearer(token))).path("data");

        assertThat(data.path("followedByMe").asBoolean()).isTrue();
        assertThat(data.path("followingMe").asBoolean()).isFalse();
        assertThat(data.path("mutual").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("主页关注状态：游客全部为 false")
    void profile_asGuest() throws Exception {
        JsonNode data = exec(get("/api/users/" + USER_3)).path("data");

        assertThat(data.path("followedByMe").asBoolean()).isFalse();
        assertThat(data.path("followingMe").asBoolean()).isFalse();
        assertThat(data.path("mutual").asBoolean()).isFalse();
        assertThat(data.path("username").asText()).isEqualTo("test002");
    }

    @Test
    @DisplayName("主页：刷新页面用的 where 常量字段齐全")
    void profile_basicFields() throws Exception {
        JsonNode data = exec(get("/api/users/" + USER_2)).path("data");

        assertThat(data.path("id").asText()).isEqualTo("2");
        assertThat(data.path("nickname").asText()).isEqualTo("张三");
        assertThat(data.path("followingCount").asInt()).isEqualTo(6);
        assertThat(data.path("followerCount").asInt()).isEqualTo(14);
        assertThat(data.path("postCount").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("主页：已注销用户 → 404")
    void profile_deletedUser() throws Exception {
        mockMvc.perform(get("/api/users/" + DELETED_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("主页：被禁用的用户仍可浏览（只是不能互动）")
    void profile_bannedUserIsVisible() throws Exception {
        mockMvc.perform(get("/api/users/" + BANNED_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("banned001"));
    }

    @Test
    @DisplayName("个人中心路径没有被白名单放行：未登录访问 /api/users/me → 401")
    void me_isNotWhitelisted() throws Exception {
        // 白名单用 {id:[0-9]+} 而不是 *，正是为了不把 /me 一起放出去
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
