package com.miqu.config;

import com.miqu.security.AuthInterceptor;
import com.miqu.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final MiquProperties properties;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 不排除 /api/files/**：上传接口同样要求登录，否则任何人都能往服务器写文件
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * 把本地上传目录暴露为静态资源。
     *
     * <p>使用绝对路径注册：开发时相对路径的解析基准是进程工作目录，
     * 从 IDE 和从命令行启动可能落在不同位置，导致上传成功却 404。
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path baseDir = Paths.get(properties.getUpload().getBaseDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(properties.getUpload().getUrlPrefix() + "/**")
                .addResourceLocations(baseDir.toUri().toString());
    }

    /**
     * CORS：前后端分离，开发期前端跑在 Vite 的 5173 端口。
     *
     * <p>使用 allowedOriginPatterns 而非 allowedOrigins("*")，
     * 因为一旦允许携带凭证，通配符来源会被 Spring 拒绝。
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
