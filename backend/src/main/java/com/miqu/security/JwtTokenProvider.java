package com.miqu.security;

import com.miqu.config.MiquProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与解析。
 *
 * <p>使用 HS256。密钥在启动时校验长度，避免配了个短密钥以为安全。
 * 解析失败一律返回 {@link Optional#empty()}，由调用方决定如何处理，
 * 本类不抛业务异常，也不打印 Token 内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final int MIN_SECRET_BYTES = 32;

    private final MiquProperties properties;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        String secret = properties.getJwt().getSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "miqu.jwt.secret 长度不足：HS256 要求至少 " + MIN_SECRET_BYTES + " 字节，当前 " + bytes.length + " 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(bytes);
    }

    /** 签发 Token。 */
    public String generate(Long userId, String username, Integer role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getJwt().getExpireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Token 有效期（秒），用于登录响应。 */
    public long getExpireSeconds() {
        return properties.getJwt().getExpireSeconds();
    }

    /** 解析 Token；签名不合法、已过期、格式错误一律返回 empty。 */
    public Optional<LoginUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, Integer.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // 只记录原因，绝不记录 token 原文
            log.debug("JWT 解析失败：{}", e.getMessage());
            return Optional.empty();
        }
    }
}
