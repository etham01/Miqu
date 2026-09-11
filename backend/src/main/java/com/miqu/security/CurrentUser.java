package com.miqu.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前登录用户，由 {@link CurrentUserArgumentResolver} 解析。
 *
 * <pre>
 * // 注入完整上下文（未登录时为 null，供"可选登录"接口使用）
 * &#64;GetMapping("/api/posts") Result&lt;PageResult&lt;PostVO&gt;&gt; list(LoginUser currentUser) { ... }
 *
 * // 只注入用户 ID，前提是该接口已要求登录
 * &#64;DeleteMapping("/api/posts/{id}") Result&lt;Void&gt; delete(&#64;CurrentUser Long userId, ...) { ... }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}
