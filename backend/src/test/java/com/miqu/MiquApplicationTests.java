package com.miqu;

import com.miqu.common.BizConstants;
import com.miqu.config.MiquProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文装配检查。
 *
 * <p>验证 Spring 容器能完整启动：所有 Bean 依赖可解析、配置项绑定正确、
 * 必填配置齐全（如 JWT 密钥长度）。这类问题在启动时就暴露，
 * 比等到某个接口被调用时才报错要好得多。
 */
@SpringBootTest
@DisplayName("应用上下文")
class MiquApplicationTests {

    @Autowired
    private MiquProperties properties;

    @Test
    @DisplayName("Spring 容器加载成功")
    void contextLoads() {
        assertThat(properties).isNotNull();
    }

    @Test
    @DisplayName("JWT 配置已正确绑定，且密钥长度满足 HS256 要求")
    void jwtPropertiesAreBound() {
        String secret = properties.getJwt().getSecret();
        assertThat(secret).isNotBlank();
        assertThat(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .as("HS256 要求密钥至少 32 字节")
                .isGreaterThanOrEqualTo(32);
        assertThat(properties.getJwt().getExpireSeconds()).isPositive();
    }

    @Test
    @DisplayName("白名单包含注册、登录与健康检查，且不包含任意放行的通配符")
    void authWhiteListIsConfigured() {
        assertThat(properties.getAuth().getWhiteList())
                .contains("POST:/api/auth/register", "POST:/api/auth/login", "GET:/api/health")
                // 白名单里出现 /api/** 会让整套鉴权形同虚设，这里守住这条底线
                .noneMatch(pattern -> pattern.endsWith("/api/**"));
    }

    @Test
    @DisplayName("分页上限与常量保持一致")
    void paginationLimitsAreSane() {
        assertThat(BizConstants.MAX_PAGE_SIZE).isPositive();
        assertThat(BizConstants.DEFAULT_PAGE_SIZE).isLessThanOrEqualTo(BizConstants.MAX_PAGE_SIZE);
    }
}
