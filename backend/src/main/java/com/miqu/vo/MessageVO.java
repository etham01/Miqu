package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "私信消息")
public record MessageVO(

        @Schema(description = "消息 ID", example = "120")
        Long id,

        @Schema(description = "所属会话 ID", example = "1")
        Long conversationId,

        @Schema(description = "发送者 ID", example = "2")
        Long senderId,

        @Schema(description = "接收者 ID", example = "3")
        Long receiverId,

        @Schema(description = "消息内容")
        String content,

        @Schema(description = "是否为当前登录用户发出，前端据此决定气泡左右", example = "true")
        Boolean mine,

        @Schema(description = "是否已读", example = "false")
        Boolean isRead,

        @Schema(description = "发送时间")
        LocalDateTime createTime
) {
}
