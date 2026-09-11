package com.miqu.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 获取或创建与某人的会话。
 *
 * <p>幂等：已存在则直接返回，不会重复创建。
 * 用于用户点开某人主页的"发私信"按钮时先拿到会话 ID。
 */
@Schema(description = "获取或创建会话请求")
public record CreateConversationRequest(

        @Schema(description = "对方用户 ID", example = "3")
        @NotNull(message = "对方用户不能为空")
        Long targetUserId
) {
}
