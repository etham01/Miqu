package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应。
 *
 * <p>登录成功后立即回传用户信息，前端无需再发一次 {@code GET /api/users/me}，
 * 减少一次往返也避免了首屏"空窗期"。
 */
@Schema(description = "登录响应")
public record LoginVO(

        @Schema(description = "JWT Token")
        String token,

        @Schema(description = "Token 类型，固定为 Bearer", example = "Bearer")
        String tokenType,

        @Schema(description = "有效期（秒）", example = "604800")
        long expiresIn,

        @Schema(description = "用户信息")
        UserVO user
) {
}
