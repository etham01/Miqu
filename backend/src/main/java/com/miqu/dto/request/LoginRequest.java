package com.miqu.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(

        @Schema(description = "登录名", example = "test001")
        @NotBlank(message = "用户名不能为空")
        String username,

        @Schema(description = "密码", example = "123456")
        @NotBlank(message = "密码不能为空")
        String password
) {
}
