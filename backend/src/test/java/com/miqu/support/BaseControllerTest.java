package com.miqu.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 接口测试基类。
 *
 * <p>使用 {@code @Transactional} 让每个用例结束后自动回滚，
 * 这样用例之间不会互相污染，也不需要手工清理数据库。
 * 注意：回滚之所以有效，是因为 MockMvc 不发真实 HTTP 请求，
 * 被测代码运行在与测试方法相同的线程和事务中。若将来改用真实端口，
 * 事务就不再共享，需要换成显式的数据清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class BaseControllerTest {

    protected static final String LOGIN_URL = "/api/auth/login";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 执行请求并解析出统一响应体。 */
    protected JsonNode exec(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            throw new AssertionError("响应体为空，HTTP 状态：" + result.getResponse().getStatus());
        }
        return objectMapper.readTree(body);
    }

    /** 用种子账号登录，返回 token。 */
    protected String login(String username, String password) throws Exception {
        JsonNode json = exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username, "password", password))));
        JsonNode token = json.path("data").path("token");
        if (token.isMissingNode() || token.asText().isBlank()) {
            throw new AssertionError("登录失败，无法获取 token：" + json);
        }
        return token.asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
