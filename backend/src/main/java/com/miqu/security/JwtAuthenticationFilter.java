package com.miqu.security;

import com.miqu.common.ErrorCode;
import com.miqu.common.enums.UserStatusEnum;
import com.miqu.config.MiquProperties;
import com.miqu.entity.User;
import com.miqu.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 *
 * <p>职责边界：**只做身份识别，不做访问控制**。
 * 解析成功则写入 {@link CurrentUserHolder}；失败则把原因放进 request attribute，
 * 由 {@link AuthInterceptor} 结合白名单决定是放行还是拒绝。
 * 这样白名单接口（如游客浏览动态）即使在携带坏 Token 时也能正常返回。
 *
 * <p><b>关键：解析后必须查库校验账号状态。</b>
 * 否则管理员禁用某用户后，对方手里已签发的 Token 在有效期内依然畅通，
 * "用户状态管理"这个功能等于没有实现。本项目规模下每请求一次主键查询完全可以接受。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因，供 AuthInterceptor 读取。 */
    public static final String AUTH_ERROR_ATTRIBUTE = "miqu.authError";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                authenticate(token, request);
            }
            filterChain.doFilter(request, response);
        } finally {
            // 必须在 finally 清理：preHandle 抛异常时 afterCompletion 不会执行，
            // 残留的 ThreadLocal 会让后续复用该线程的请求"继承"上一个用户的身份。
            CurrentUserHolder.clear();
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        jwtTokenProvider.parse(token).ifPresentOrElse(
                loginUser -> {
                    User user = userMapper.selectById(loginUser.userId());
                    if (user == null) {
                        // 账号已被逻辑删除（MP 的逻辑删除会自动过滤，查不到即为已删）
                        request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
                    } else if (!UserStatusEnum.isNormal(user.getStatus())) {
                        request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.USER_DISABLED);
                    } else {
                        CurrentUserHolder.set(
                                new LoginUser(user.getId(), user.getUsername(), user.getRole()));
                    }
                },
                () -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
