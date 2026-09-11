package com.miqu.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户搜索接口测试。
 *
 * <p>种子事实：
 * <ul>
 *   <li>昵称含"张"的只有 test001（张三）</li>
 *   <li>用户名含 test00 的有 9 个（test001 ~ test009）</li>
 *   <li>没有任何用户的昵称或用户名里含有 {@code %} 或 {@code _}</li>
 * </ul>
 */
@DisplayName("用户搜索 /api/users/search")
class UserSearchTest extends BaseControllerTest {

    private static final String SEARCH = "/api/users/search";

    /**
     * 用 {@code .param()} 传参，而不是把关键词拼进 URL。
     *
     * <p>MockMvc 会对 URL 模板再做一次百分号编码，"張" 被编码成 {@code %E5%BC%A0} 后会变成
     * {@code %25E5%25BC%25A0}，服务端拿到的是一串字面量而不是中文，测试必然查不到东西。
     * 这是 MockMvc 的行为，不是接口的问题——已用 curl 对真实服务器验证过
     * 百分号编码的中文关键词能正确命中（见 README 端到端说明）。
     */
    private JsonNode search(String keyword, String... extraParams) throws Exception {
        MockHttpServletRequestBuilder builder = get(SEARCH).param("keyword", keyword);
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            builder.param(extraParams[i], extraParams[i + 1]);
        }
        return exec(builder);
    }

    // ==================== 基本匹配 ====================

    @Test
    @DisplayName("按昵称搜索：搜「张」命中 test001")
    void search_byNickname() throws Exception {
        JsonNode json = search("张");

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(1);

        JsonNode first = json.path("data").path("list").get(0);
        assertThat(first.path("username").asText()).isEqualTo("test001");
        assertThat(first.path("nickname").asText()).isEqualTo("张三");
        assertThat(first.path("followerCount").asInt()).isEqualTo(14);
    }

    @Test
    @DisplayName("按昵称搜索：支持部分匹配（搜「小明」命中 test011 的昵称「陈小明」）")
    void search_byNicknamePartial() throws Exception {
        JsonNode json = search("小明");

        assertThat(json.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(json.path("data").path("list").get(0).path("username").asText())
                .isEqualTo("test011");
    }

    @Test
    @DisplayName("按用户名搜索：搜 test00 命中 9 个用户，按粉丝数倒序")
    void search_byUsername() throws Exception {
        JsonNode json = search("test00", "size", "50");

        assertThat(json.path("data").path("total").asLong()).isEqualTo(9);

        // test001 粉丝最多（14），应排在最前
        JsonNode list = json.path("data").path("list");
        assertThat(list.get(0).path("username").asText()).isEqualTo("test001");

        long previous = Long.MAX_VALUE;
        for (JsonNode item : list) {
            long followerCount = item.path("followerCount").asLong();
            assertThat(followerCount).isLessThanOrEqualTo(previous);
            previous = followerCount;
        }
    }

    @Test
    @DisplayName("搜索结果不含邮箱等非公开字段")
    void search_hidesPrivateFields() throws Exception {
        JsonNode first = search("test00").path("data").path("list").get(0);

        assertThat(first.has("email")).isFalse();
        assertThat(first.has("password")).isFalse();
    }

    @Test
    @DisplayName("搜索无结果时返回空列表而不是报错")
    void search_noMatch() throws Exception {
        JsonNode json = search("zzzznotexist");

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isZero();
        assertThat(json.path("data").path("list")).isEmpty();
    }

    // ==================== LIKE 通配符转义 ====================

    /**
     * 这是本模块最容易被忽略、也最容易被测试挖出来的边界。
     *
     * <p>如果不对关键词里的 {@code %} 做转义，它会作为 LIKE 的通配符生效，
     * 用户搜一个 {@code %} 就能把**全部用户**翻出来——既是信息泄漏，
     * 也是典型的注入式输入。正确行为是把它当普通字符，匹配不到任何结果。
     */
    @Test
    @DisplayName("关键词 % 被当作普通字符，不会匹配全部用户")
    void search_percentIsEscaped() throws Exception {
        JsonNode json = search("%", "size", "50");

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong())
                .as("百分号必须被转义，否则会返回全部用户")
                .isZero();
    }

    @Test
    @DisplayName("关键词 _ 被当作普通字符，不会匹配任意单字符")
    void search_underscoreIsEscaped() throws Exception {
        JsonNode json = search("_", "size", "50");

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong())
                .as("下划线必须被转义，否则会匹配任意单字符")
                .isZero();
    }

    @Test
    @DisplayName("关键词 %test00% 被当作字面量，匹配不到任何结果")
    void search_wrappedWildcardIsEscaped() throws Exception {
        JsonNode json = search("%test00%", "size", "50");

        // 未转义时会退化成等同于 test00 的搜索，返回 9 条
        assertThat(json.path("data").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("关键词含反斜杠不会破坏 ESCAPE 子句")
    void search_backslashIsEscaped() throws Exception {
        JsonNode json = search("\\", "size", "50");

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isZero();
    }

    // ==================== 关注状态 ====================

    @Test
    @DisplayName("游客搜索：followedByMe 恒为 false")
    void search_asGuest() throws Exception {
        JsonNode list = search("test00", "size", "50").path("data").path("list");

        for (JsonNode item : list) {
            assertThat(item.path("followedByMe").asBoolean()).isFalse();
        }
    }

    @Test
    @DisplayName("登录用户搜索：followedByMe 反映真实关注关系")
    void search_followedByMeFlag() throws Exception {
        // test001 关注的是 3、4、5、6、7、8（即 test002 ~ test007）
        String token = login("test001", "123456");
        JsonNode list = exec(get(SEARCH).param("keyword", "test00").param("size", "50")
                .header("Authorization", bearer(token))).path("data").path("list");

        int followed = 0;
        for (JsonNode item : list) {
            if (item.path("followedByMe").asBoolean()) {
                followed++;
            }
        }
        // test001 自己也在搜索结果里，但不关注自己
        assertThat(followed).isEqualTo(6);
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("关键词为空 → 400")
    void search_blankKeyword() throws Exception {
        mockMvc.perform(get(SEARCH).param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("搜索关键词不能为空"));
    }

    @Test
    @DisplayName("缺少关键词参数 → 400")
    void search_missingKeyword() throws Exception {
        mockMvc.perform(get(SEARCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("关键词超过 32 字符 → 400")
    void search_keywordTooLong() throws Exception {
        mockMvc.perform(get(SEARCH).param("keyword", "a".repeat(33)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("搜索关键词不能超过 32 个字符"));
    }

    @Test
    @DisplayName("搜索分页：size 超过上限 → 400")
    void search_sizeOverLimit() throws Exception {
        mockMvc.perform(get(SEARCH).param("keyword", "test00").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("搜索免登录：白名单已放行 /api/users/search")
    void search_isPublic() throws Exception {
        mockMvc.perform(get(SEARCH).param("keyword", "test00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
