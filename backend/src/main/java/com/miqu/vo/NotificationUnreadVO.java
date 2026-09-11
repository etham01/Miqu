package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通知未读数（按类型拆开）。
 *
 * <p>只统计关注/点赞/评论三类。**私信不计入**——它由
 * {@code /api/messages/unread-count} 与会话列表的未读数承载，
 * 避免同一件事在两个地方重复计数。
 */
@Schema(description = "通知未读数")
public record NotificationUnreadVO(

        @Schema(description = "未读总数", example = "7")
        long total,

        @Schema(description = "未读的关注数", example = "1")
        long follow,

        @Schema(description = "未读的点赞数", example = "4")
        long like,

        @Schema(description = "未读的评论数", example = "2")
        long comment
) {
}
