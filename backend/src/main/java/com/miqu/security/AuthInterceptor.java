package com.miqu.security;

import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.config.MiquProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 访问控制拦截器。
 *
 * <p>策略是**失败关闭**：除白名单外，{@code /api/**} 一律要求登录。
 * 新增接口若忘记加白名单，默认是受保护的，而不是裸奔。
 *
 * <p>与 {@link JwtAuthenticationFilter} 的分工：
 * 过滤器负责"你是谁"（解析 Token、查库校验状态），本类负责"你能不能进"。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final MiquProperties properties;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String uri = request.getRequestURI().substring(request.getContextPath().length());
        boolean whiteListed = isWhiteListed(request, uri);
        ErrorCode authError = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        LoginUser currentUser = CurrentUserHolder.get();

        // 1. 需要管理员：未登录 401，已登录但非管理员 403
        boolean adminRequired = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
        if (adminRequired) {
            if (currentUser == null) {
                throw BizException.of(authError != null ? authError : ErrorCode.UNAUTHORIZED);
            }
            if (!currentUser.isAdmin()) {
                throw BizException.of(ErrorCode.FORBIDDEN);
            }
            return true;
        }

        // 2. 白名单外必须登录。带坏 Token 访问白名单接口时按游客放行，
        //    这样游客浏览动态不会因为本地残留了过期 Token 而直接报错。
        if (!whiteListed && currentUser == null) {
            throw BizException.of(authError != null ? authError : ErrorCode.UNAUTHORIZED);
        }

        return true;
    }

    /**
     * 匹配白名单，条目格式：{@code METHODS:/ant/path}，METHODS 可省略表示任意方法。
     */
    private boolean isWhiteListed(HttpServletRequest request, String uri) {
        for (String entry : properties.getAuth().getWhiteList()) {
            String pattern = entry;
            int idx = entry.indexOf(':');
            if (idx > 0) {
                String method = entry.substring(0, idx);
                if (!method.equalsIgnoreCase(request.getMethod())) {
                    continue;
                }
                pattern = entry.substring(idx + 1);
            }
            if (PATH_MATCHER.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }
}
