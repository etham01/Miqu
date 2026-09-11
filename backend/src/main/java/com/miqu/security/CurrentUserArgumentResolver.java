package com.miqu.security;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 把 {@link CurrentUserHolder} 中的登录用户注入到 Controller 方法参数。
 *
 * <p>支持两种形态：
 * <ul>
 *   <li>{@code LoginUser} 类型参数——直接注入，未登录时为 {@code null}</li>
 *   <li>{@code @CurrentUser Long} 参数——只注入 userId</li>
 * </ul>
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return LoginUser.class.isAssignableFrom(parameter.getParameterType())
                || (parameter.hasParameterAnnotation(CurrentUser.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType()));
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        if (Long.class.isAssignableFrom(parameter.getParameterType())) {
            return CurrentUserHolder.userId();
        }
        return CurrentUserHolder.get();
    }
}
