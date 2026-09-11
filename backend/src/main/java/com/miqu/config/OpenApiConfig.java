package com.miqu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 配置。
 *
 * <p>访问：{@code http://localhost:8080/swagger-ui.html}
 *
 * <p>注册了 Bearer 认证方案后，Swagger UI 上会出现 Authorize 按钮，
 * 填入登录返回的 token 即可直接调试需要登录的接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI miquOpenAPI() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("登录接口返回的 token，填写时无需手动加 Bearer 前缀");

        return new OpenAPI()
                .info(new Info()
                        .title("Miqu「觅取」社交系统 API")
                        .version("v1.0.0")
                        .description("""
                                轻量级社交平台后端接口。

                                **统一响应格式**：`{ "code": 200, "message": "success", "data": {...} }`

                                **错误码约定**（与 HTTP 语义对齐）：
                                - `400` 参数校验失败
                                - `401` 未登录 / Token 无效或过期
                                - `403` 无权限（如删除他人动态）
                                - `404` 资源不存在
                                - `409` 资源冲突（重复注册、重复关注、重复点赞）
                                - `423` 账号已被禁用
                                - `500` 系统异常

                                **注意**：所有 `id` 字段序列化为字符串，避免前端 JS 精度丢失。
                                """)
                        .contact(new Contact().name("Miqu")))
                .components(new Components().addSecuritySchemes(SCHEME_NAME, bearer))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
