package com.miqu.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@code database/data.sql} 中硬编码的 BCrypt 哈希确实对应文档里写的测试密码。
 *
 * <p>种子数据里的密码是预先算好的哈希，一旦有人改了文档中的测试密码
 * 却忘了同步哈希，所有依赖种子账号的测试都会以"登录失败"的形式失败，
 * 而失败原因很难一眼看出来。这条用例把它变成一个明确的断言。
 *
 * <p>不依赖 Spring 上下文与数据库，是最快的一道防线。
 */
@DisplayName("种子数据：测试账号密码哈希")
class SeedPasswordTest {

    /** 与 database/data.sql 中所有种子用户的 password 字段保持一致。 */
    private static final String SEED_HASH =
            "$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm";

    private static final String SEED_PASSWORD = "123456";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("data.sql 中的哈希匹配文档声明的测试密码 123456")
    void seedHashMatchesDocumentedPassword() {
        assertThat(passwordEncoder.matches(SEED_PASSWORD, SEED_HASH))
                .as("data.sql 的 BCrypt 哈希必须对应测试密码 %s", SEED_PASSWORD)
                .isTrue();
    }

    @Test
    @DisplayName("data.sql 中的哈希不代表其他密码")
    void seedHashDoesNotMatchOtherPasswords() {
        assertThat(passwordEncoder.matches("1234567", SEED_HASH)).isFalse();
        assertThat(passwordEncoder.matches("password", SEED_HASH)).isFalse();
        assertThat(passwordEncoder.matches("", SEED_HASH)).isFalse();
    }
}
