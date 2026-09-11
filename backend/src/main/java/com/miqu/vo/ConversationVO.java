package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 *
 * <p>{@code unreadCount} 取的是 {@code conversation.user*_unread} —— 它是未读数的**权威值**；
 * {@code message.is_read} 只用于聊天记录里渲染每一条的已读状态。
 */
@Schema(description = "会话信息")
public record ConversationVO(

        @Schema(description = "会话 ID", example = "1")
        Long id,

        @Schema(description = "对方用户信息")
        UserBriefVO partner,

        @Schema(description = "最后一条消息的预览，超长已截断")
        String lastMessagePreview,

        @Schema(description = "最后一条消息的时间；会话列表按此倒序")
        LocalDateTime lastMessageTime,

        @Schema(description = "当前用户在该会话中的未读数", example = "2")
        Integer unreadCount
) {
}
