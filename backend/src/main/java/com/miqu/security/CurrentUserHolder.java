package com.miqu.security;

/**
 * 当前请求的登录用户上下文（ThreadLocal）。
 *
 * <p>由 {@link JwtAuthenticationFilter} 写入，并在 **finally 中清理**。
 * 清理动作不能依赖拦截器的 afterCompletion —— 一旦请求在 preHandle 阶段就抛异常，
 * afterCompletion 不会执行，ThreadLocal 会残留在复用的线程上，造成身份串号。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 未登录时返回 null。 */
    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
