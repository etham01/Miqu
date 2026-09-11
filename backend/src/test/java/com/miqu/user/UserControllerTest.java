package com.miqu.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.security.JwtTokenProvider;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("用户接口 /api/users")
class UserControllerTest extends BaseControllerTest {

    private static final String ME = "/api/users/me";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // ==================== 认证拦截 ====================

    @Test
    @DisplayName("未携带 Token 访问 /me → 401")
    void me_withoutToken() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录状态已失效"));
    }

    @Test
    @DisplayName("携带伪造 Token 访问 /me → 401")
    void me_withMalformedToken() throws Exception {
        mockMvc.perform(get(ME).header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Authorization 头缺少 Bearer 前缀 → 401")
    void me_withoutBearerPrefix() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(get(ME).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 这条用例守护的是一个容易被忽略的安全缺口：
     * 如果 JWT 过滤器只验签名、不查库校验账号状态，
     * 那么管理员禁用某用户后，对方手里已签发的 Token 在 7 天有效期内依旧畅通，
     * "用户状态管理"功能实际上等于没实现。
     */
    @Test
    @DisplayName("账号被禁用后，已签发的旧 Token 立即失效 → 423")
    void me_tokenIssuedBeforeBanIsRejected() throws Exception {
        // banned001（id=12）状态为禁用，无法通过登录接口拿到 token，
        // 因此直接签发一个合法 Token，模拟"禁用之前就已经登录"的场景
        String token = jwtTokenProvider.generate(12L, "banned001", 1);

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(423))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("已注销账号的 Token 失效 → 401")
    void me_tokenOfDeletedUserIsRejected() throws Exception {
        String token = jwtTokenProvider.generate(13L, "deleted001", 1);

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Token 指向不存在的用户 → 401")
    void me_tokenOfNonexistentUserIsRejected() throws Exception {
        String token = jwtTokenProvider.generate(999999L, "ghost", 1);

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 查询当前用户 ====================

    @Test
    @DisplayName("携带有效 Token 访问 /me → 200，返回完整用户信息且不含密码")
    void me_withValidToken() throws Exception {
        String token = login("test001", "123456");

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("2"))
                .andExpect(jsonPath("$.data.username").value("test001"))
                .andExpect(jsonPath("$.data.nickname").value("张三"))
                .andExpect(jsonPath("$.data.email").value("test001@miqu.com"))
                .andExpect(jsonPath("$.data.role").value(1))
                .andExpect(jsonPath("$.data.followingCount").isNumber())
                .andExpect(jsonPath("$.data.followerCount").isNumber())
                .andExpect(jsonPath("$.data.postCount").isNumber())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    // ==================== 修改资料 ====================

    @Test
    @DisplayName("修改资料：昵称、性别、简介更新成功")
    void updateProfile_success() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"nickname":"张三丰","gender":2,"birthday":"1999-01-01","bio":"更新后的简介"}
                """;

        mockMvc.perform(put(ME).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("张三丰"))
                .andExpect(jsonPath("$.data.gender").value(2))
                .andExpect(jsonPath("$.data.birthday").value("1999-01-01"))
                .andExpect(jsonPath("$.data.bio").value("更新后的简介"))
                // 用户名与邮箱不可通过该接口修改
                .andExpect(jsonPath("$.data.username").value("test001"))
                .andExpect(jsonPath("$.data.email").value("test001@miqu.com"));
    }

    @Test
    @DisplayName("修改资料：昵称为空 → 400")
    void updateProfile_blankNickname() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"nickname":"","gender":1}
                """;

        mockMvc.perform(put(ME).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("修改资料：性别取值非法 → 400")
    void updateProfile_invalidGender() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"nickname":"张三","gender":9}
                """;

        mockMvc.perform(put(ME).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("性别取值只能是 0、1 或 2"));
    }

    @Test
    @DisplayName("修改资料：未登录 → 401")
    void updateProfile_requiresLogin() throws Exception {
        mockMvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 修改头像 ====================

    @Test
    @DisplayName("修改头像成功")
    void updateAvatar_success() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"avatar":"/uploads/image/2026/09/newavatar.jpg"}
                """;

        mockMvc.perform(put(ME + "/avatar").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.avatar").value("/uploads/image/2026/09/newavatar.jpg"));
    }

    @Test
    @DisplayName("修改头像：地址为空 → 400")
    void updateAvatar_blank() throws Exception {
        String token = login("test001", "123456");

        mockMvc.perform(put(ME + "/avatar").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("头像地址不能为空"));
    }

    // ==================== 修改密码 ====================

    @Test
    @DisplayName("修改密码：原密码错误 → 400")
    void changePassword_wrongOldPassword() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"oldPassword":"wrong","newPassword":"newpass123"}
                """;

        mockMvc.perform(put(ME + "/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("原密码不正确"));
    }

    @Test
    @DisplayName("修改密码：新密码与原密码相同 → 400")
    void changePassword_sameAsOld() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"oldPassword":"123456","newPassword":"123456"}
                """;

        mockMvc.perform(put(ME + "/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同"));
    }

    @Test
    @DisplayName("修改密码：新密码长度不足 → 400")
    void changePassword_newPasswordTooShort() throws Exception {
        String token = login("test001", "123456");
        String body = """
                {"oldPassword":"123456","newPassword":"123"}
                """;

        mockMvc.perform(put(ME + "/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("新密码长度必须在 6~20 个字符之间"));
    }

    @Test
    @DisplayName("修改密码成功：旧密码随后失效，新密码可登录")
    void changePassword_success_oldPasswordNoLongerWorks() throws Exception {
        String token = login("test001", "123456");

        mockMvc.perform(put(ME + "/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"123456\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 旧密码登录失败
        JsonNode oldLogin = exec(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test001\",\"password\":\"123456\"}"));
        assertThat(oldLogin.path("code").asInt()).isEqualTo(401);

        // 新密码登录成功
        JsonNode newLogin = exec(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test001\",\"password\":\"newpass123\"}"));
        assertThat(newLogin.path("code").asInt()).isEqualTo(200);
        assertThat(newLogin.path("data").path("token").asText()).isNotBlank();
    }

    // ==================== 健康检查 ====================

    @Test
    @DisplayName("健康检查接口免登录可访问")
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
