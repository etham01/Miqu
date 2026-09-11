package com.miqu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>只引入 {@code spring-security-crypto} 这一个 jar，不引入完整的 Spring Security。
 * 本项目的认证是"自定义 Filter + 拦截器"模型，引入整套 Security 过滤链会带来
 * 大量需要关闭的默认行为，反而降低可读性。
 *
 * <p>选择 BCrypt 而非 MD5/SHA：自带盐值、可调计算强度，是密码存储的默认正确答案。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
