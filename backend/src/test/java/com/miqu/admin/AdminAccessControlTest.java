package com.miqu.admin;

import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台的访问控制矩阵。
 *
 * <p>单独成类是因为这条边界覆盖所有后台接口，逐个接口重复断言太啰嗦，
 * 集中一处更容易看出"有没有哪个接口漏了保护"。
 *
 * <p>三种失败原因必须可区分：
 * <ul>
 *   <li>未登录 → 401（连身份都没有）</li>
 *   <li>已登录但非管理员 → 403（身份有，权限不够）</li>
 * </ul>
 * 混成同一个码，前端与自动化测试都无法判断该引导用户去登录还是提示无权限。
 */
@DisplayName("管理后台 · 访问控制")
class AdminAccessControlTest extends BaseControllerTest {

    /** 所有后台接口，格式：{METHOD, 路径}。新增后台接口时请一并登记。 */
    private static final String[][] ADMIN_ENDPOINTS = {
            {"GET", "/api/admin/stats"},
            {"GET", "/api/admin/users"},
            {"GET", "/api/admin/users/2"},
            {"PUT", "/api/admin/users/2/status"},
            {"GET", "/api/admin/posts"},
            {"DELETE", "/api/admin/posts/1"},
            {"GET", "/api/admin/comments"},
            {"DELETE", "/api/admin/comments/1"},
            {"GET", "/api/admin/reports"},
            {"PUT", "/api/admin/reports/1/handle"},
            {"GET", "/api/admin/logs"},
    };

    private MockHttpServletRequestBuilder build(String[] endpoint) {
        return switch (endpoint[0]) {
            case "GET" -> get(endpoint[1]);
            case "PUT" -> put(endpoint[1]).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(endpoint[1]);
            default -> throw new IllegalArgumentException(endpoint[0]);
        };
    }

    @Test
    @DisplayName("未登录访问任何后台接口 → 401")
    void allEndpoints_requireLogin() throws Exception {
        for (String[] endpoint : ADMIN_ENDPOINTS) {
            mockMvc.perform(build(endpoint))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    @Test
    @DisplayName("普通用户访问任何后台接口 → 403")
    void allEndpoints_forbidNormalUser() throws Exception {
        String token = login("test001", "123456");

        for (String[] endpoint : ADMIN_ENDPOINTS) {
            mockMvc.perform(build(endpoint).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    @Test
    @DisplayName("403 与 401 的文案必须不同，前端才能区分处理")
    void forbiddenMessageDiffersFromUnauthorized() throws Exception {
        String userToken = login("test001", "123456");

        String unauthorized = exec(get("/api/admin/stats")).path("message").asText();
        String forbidden = exec(get("/api/admin/stats").header("Authorization", bearer(userToken)))
                .path("message").asText();

        assertThat(unauthorized).isNotEqualTo(forbidden);
        assertThat(forbidden).isEqualTo("无权限执行该操作");
        assertThat(unauthorized).isEqualTo("未登录或登录状态已失效");
    }

    @Test
    @DisplayName("管理员可以访问后台接口")
    void adminCanAccess() throws Exception {
        String token = login("admin", "123456");

        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
