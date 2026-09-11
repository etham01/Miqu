package com.miqu.auth;

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
 * 认证接口测试。
 *
 * <p>关于断言精度的说明：Bean Validation 对同一字段可能同时触发多条约束
 * （例如空用户名会同时违反 @NotBlank、@Size、@Pattern），
 * 而校验器不保证触发顺序，因此"究竟返回哪条文案"是不确定的。
 * 本类的做法是：
 * <ul>
 *   <li>能构造出<b>只违反一条约束</b>的输入时，断言精确文案（如用户名仅长度不足）</li>
 *   <li>无法避免多条约束同时命中时，只断言错误码，并在用例里注明原因</li>
 * </ul>
 * 这样测试既严格又不会因为校验器内部顺序变化而变成假失败。
 */
@DisplayName("认证接口 /api/auth")
class AuthControllerTest extends BaseControllerTest {

    private static final String REGISTER = "/api/auth/register";

    private static final String VALID_REGISTER_BODY = """
            {"username":"newuser001","password":"123456","nickname":"新用户",
             "email":"newuser001@miqu.com","gender":1,"birthday":"1998-03-15","bio":"你好"}
            """;

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册成功：返回用户信息，且响应中不含密码字段")
    void register_success() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.username").value("newuser001"))
                .andExpect(jsonPath("$.data.nickname").value("新用户"))
                .andExpect(jsonPath("$.data.email").value("newuser001@miqu.com"))
                // 新注册用户一律是普通用户，不能通过请求体把自己变成管理员
                .andExpect(jsonPath("$.data.role").value(1))
                .andExpect(jsonPath("$.data.followingCount").value(0))
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.postCount").value(0))
                // 密码绝不出现在任何响应中
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("注册：不传性别时默认为 0（未知）")
    void register_genderDefaultsToUnknown() throws Exception {
        String body = """
                {"username":"newuser002","password":"123456","nickname":"新用户2","email":"newuser002@miqu.com"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value(0))
                .andExpect(jsonPath("$.data.bio").value(""));
    }

    @Test
    @DisplayName("注册：用户名长度不足 4 位 → 400")
    void register_usernameTooShort() throws Exception {
        String body = """
                {"username":"abc","password":"123456","nickname":"新用户","email":"abc@miqu.com"}
                """;
        // "abc" 以字母开头且只含字母，因此只违反长度约束，文案可精确断言
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名长度必须在 4~20 个字符之间"));
    }

    @Test
    @DisplayName("注册：用户名以数字开头 → 400")
    void register_usernameMustStartWithLetter() throws Exception {
        String body = """
                {"username":"1abcde","password":"123456","nickname":"新用户","email":"abc@miqu.com"}
                """;
        // 长度合法，只违反字符规则
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名必须以字母开头，且只能包含字母、数字和下划线"));
    }

    @Test
    @DisplayName("注册：用户名为空 → 400（同时违反多条约束，只断言错误码）")
    void register_usernameBlank() throws Exception {
        String body = """
                {"username":"","password":"123456","nickname":"新用户","email":"blank@miqu.com"}
                """;
        // 空字符串同时违反 @NotBlank / @Size / @Pattern，触发顺序不确定，故不断言具体文案
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("注册：密码长度不足 6 位 → 400")
    void register_passwordTooShort() throws Exception {
        String body = """
                {"username":"newuser003","password":"12345","nickname":"新用户","email":"newuser003@miqu.com"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度必须在 6~20 个字符之间"));
    }

    @Test
    @DisplayName("注册：邮箱格式错误 → 400")
    void register_emailInvalid() throws Exception {
        String body = """
                {"username":"newuser004","password":"123456","nickname":"新用户","email":"not-an-email"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱格式不正确"));
    }

    @Test
    @DisplayName("注册：用户名已存在 → 409")
    void register_duplicateUsername() throws Exception {
        // test001 是 data.sql 中的种子用户
        String body = """
                {"username":"test001","password":"123456","nickname":"冒名者","email":"another@miqu.com"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已被占用"));
    }

    @Test
    @DisplayName("注册：邮箱已存在 → 409")
    void register_duplicateEmail() throws Exception {
        String body = """
                {"username":"anotheruser","password":"123456","nickname":"新用户","email":"test001@miqu.com"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("邮箱已被注册"));
    }

    @Test
    @DisplayName("注册：邮箱大小写归一化，Test001@Miqu.com 与已有邮箱冲突")
    void register_emailIsCaseInsensitive() throws Exception {
        String body = """
                {"username":"anotheruser","password":"123456","nickname":"新用户","email":"TEST001@MIQU.COM"}
                """;
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：返回 token 与用户信息")
    void login_success() throws Exception {
        String body = """
                {"username":"test001","password":"123456"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(604800))
                .andExpect(jsonPath("$.data.user.username").value("test001"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    @DisplayName("登录：密码错误 → 401")
    void login_wrongPassword() throws Exception {
        String body = """
                {"username":"test001","password":"wrong-password"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("登录：用户不存在 → 401，且文案与密码错误完全一致（防用户名枚举）")
    void login_userNotFoundReturnsSameMessageAsWrongPassword() throws Exception {
        String body = """
                {"username":"nosuchuser","password":"123456"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("登录：账号被禁用 → 423")
    void login_disabledUser() throws Exception {
        // banned001 是 data.sql 中 status=0 的种子用户
        String body = """
                {"username":"banned001","password":"123456"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(423))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("登录：账号已注销 → 401（逻辑删除后查不到该用户）")
    void login_deletedUser() throws Exception {
        // deleted001 是 data.sql 中 deleted=1 的种子用户
        String body = """
                {"username":"deleted001","password":"123456"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录：用户名为空 → 400")
    void login_blankUsername() throws Exception {
        String body = """
                {"username":"","password":"123456"}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名不能为空"));
    }

    @Test
    @DisplayName("登录：密码为空 → 400")
    void login_blankPassword() throws Exception {
        String body = """
                {"username":"test001","password":""}
                """;
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    // ==================== 退出登录 ====================

    @Test
    @DisplayName("退出登录：未登录时被拦截 → 401")
    void logout_requiresLogin() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("退出登录：携带有效 token → 200")
    void logout_withToken() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(post("/api/auth/logout")
                .header("Authorization", bearer(token)));
        assertThat(json.path("code").asInt()).isEqualTo(200);
    }
}
