package com.miqu.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台用户管理。
 *
 * <p>种子事实：21 个用户，其中 admin(id=1) 是唯一的管理员，
 * banned001(id=12) 已禁用，deleted001(id=13) 已注销（逻辑删除，后台列表里看不到）。
 */
@DisplayName("管理后台 · 用户管理")
class AdminUserControllerTest extends BaseControllerTest {

    private static final String USERS = "/api/admin/users";

    private String adminToken() throws Exception {
        return login("admin", "123456");
    }

    // ==================== 列表与详情 ====================

    @Test
    @DisplayName("用户列表：表里 21 行，但已注销的不出现，共 20 个")
    void list() throws Exception {
        JsonNode json = exec(get(USERS + "?size=50").header("Authorization", bearer(adminToken())));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(20);

        // 已注销用户被逻辑删除过滤掉
        for (JsonNode item : json.path("data").path("list")) {
            assertThat(item.path("username").asText()).isNotEqualTo("deleted001");
        }
    }

    @Test
    @DisplayName("用户列表：不返回密码字段")
    void list_hidesPassword() throws Exception {
        JsonNode first = exec(get(USERS).header("Authorization", bearer(adminToken())))
                .path("data").path("list").get(0);

        assertThat(first.has("password")).isFalse();
        // 后台需要看到状态字段
        assertThat(first.has("status")).isTrue();
    }

    @Test
    @DisplayName("用户列表：按关键词搜索用户名或昵称")
    void list_filterByKeyword() throws Exception {
        String token = adminToken();

        assertThat(exec(get(USERS).param("keyword", "test001").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(1);

        // 中文昵称
        assertThat(exec(get(USERS).param("keyword", "张三").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("用户列表：关键词中的 LIKE 通配符被转义")
    void list_keywordWildcardIsEscaped() throws Exception {
        JsonNode json = exec(get(USERS).param("keyword", "%").param("size", "50")
                .header("Authorization", bearer(adminToken())));

        assertThat(json.path("data").path("total").asLong())
                .as("百分号必须被转义，否则后台搜索会返回全部用户")
                .isZero();
    }

    @Test
    @DisplayName("用户列表：按状态过滤")
    void list_filterByStatus() throws Exception {
        String token = adminToken();

        // 存活用户里只有 banned001 是禁用状态
        JsonNode disabled = exec(get(USERS + "?status=0").header("Authorization", bearer(token)))
                .path("data");
        assertThat(disabled.path("total").asLong()).isEqualTo(1);
        assertThat(disabled.path("list").get(0).path("username").asText()).isEqualTo("banned001");

        // 余下 19 个正常：20 个存活用户减去 1 个被禁用的。
        // 注意 deleted001 的 status 也是 1，但它被逻辑删除，不计入
        assertThat(exec(get(USERS + "?status=1").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(19);
    }

    @Test
    @DisplayName("用户列表：状态取值非法 → 400")
    void list_invalidStatus() throws Exception {
        mockMvc.perform(get(USERS + "?status=9").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("用户详情")
    void detail() throws Exception {
        JsonNode data = exec(get(USERS + "/2").header("Authorization", bearer(adminToken()))).path("data");

        assertThat(data.path("username").asText()).isEqualTo("test001");
        assertThat(data.path("status").asInt()).isEqualTo(1);
        assertThat(data.path("followerCount").asInt()).isEqualTo(14);
    }

    @Test
    @DisplayName("用户详情：不存在 → 404")
    void detail_notFound() throws Exception {
        mockMvc.perform(get(USERS + "/999999").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 禁用 / 解禁 ====================

    @Test
    @DisplayName("禁用用户成功，且该用户已签发的旧 Token 立即失效")
    void disableUser_takesEffectImmediately() throws Exception {
        // 先让 test003 登录拿到 Token
        String victimToken = login("test003", "123456");
        assertThat(exec(get("/api/users/me").header("Authorization", bearer(victimToken)))
                .path("code").asInt()).isEqualTo(200);

        // 管理员禁用
        assertThat(exec(put(USERS + "/4/status").header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .path("code").asInt()).isEqualTo(200);

        // 旧 Token 立刻失效 —— 这是"每请求查库校验状态"换来的
        assertThat(exec(get("/api/users/me").header("Authorization", bearer(victimToken)))
                .path("code").asInt()).isEqualTo(423);

        // 也无法重新登录
        assertThat(exec(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test003\",\"password\":\"123456\"}"))
                .path("code").asInt()).isEqualTo(423);
    }

    @Test
    @DisplayName("解禁用户后可以重新登录")
    void enableUser() throws Exception {
        String token = adminToken();

        exec(put(USERS + "/12/status").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":1}"));

        assertThat(exec(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"banned001\",\"password\":\"123456\"}"))
                .path("code").asInt()).isEqualTo(200);
    }

    @Test
    @DisplayName("禁用自己 → 400（防止管理员把自己锁在外面）")
    void disableSelf() throws Exception {
        mockMvc.perform(put(USERS + "/1/status").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能对自己执行该操作"));
    }

    @Test
    @DisplayName("状态取值非法 → 400")
    void invalidStatus() throws Exception {
        mockMvc.perform(put(USERS + "/2/status").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("状态只能是 0（禁用）或 1（正常）"));
    }

    @Test
    @DisplayName("操作不存在的用户 → 404")
    void status_userNotFound() throws Exception {
        mockMvc.perform(put(USERS + "/999999/status").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ==================== 操作日志 ====================

    @Test
    @DisplayName("禁用用户会写入操作日志")
    void disableUser_writesOperationLog() throws Exception {
        String token = adminToken();
        long before = exec(get("/api/admin/logs").header("Authorization", bearer(token)))
                .path("data").path("total").asLong();

        exec(put(USERS + "/4/status").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"));

        JsonNode logs = exec(get("/api/admin/logs").header("Authorization", bearer(token))).path("data");
        assertThat(logs.path("total").asLong()).isEqualTo(before + 1);

        JsonNode latest = logs.path("list").get(0);
        assertThat(latest.path("operationType").asText()).isEqualTo("DISABLE_USER");
        assertThat(latest.path("targetId").asText()).isEqualTo("4");
        assertThat(latest.path("admin").path("username").asText()).isEqualTo("admin");
    }
}
