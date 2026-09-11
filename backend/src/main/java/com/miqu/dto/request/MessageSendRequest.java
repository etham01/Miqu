package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送私信请求。
 *
 * <p>只按 {@code receiverId} 发送，不需要客户端先拿到 conversationId：
 * 会话由服务端按 (较小 ID, 较大 ID) 规整后自动创建或复用，
 * 客户端少一次往返，也避免了"会话还没建出来"的时序问题。
 */
@Schema(description = "发送私信请求")
public record MessageSendRequest(

        @Schema(description = "接收者用户 ID", example = "3")
        @NotNull(message = "接收者不能为空")
        Long receiverId,

        @Schema(description = "消息内容，1~1000 字符", example = "在吗？想请教你一个问题。")
        @NotBlank(message = "消息内容不能为空")
        @Size(max = BizConstants.MESSAGE_CONTENT_MAX, message = "消息内容不能超过 1000 个字符")
        String content
) {
}
