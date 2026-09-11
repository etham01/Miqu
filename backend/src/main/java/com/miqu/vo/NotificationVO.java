package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 通知列表项。
 *
 * <p>{@code content} 是创建通知时写入的**内容快照**（如评论摘要）。
 * 关联的动态或评论被删除后，靠它仍能渲染出可读的条目，不会变成空白。
 *
 * <p>前端渲染建议：{@code postId} 对应的动态可能已被删除，
 * 点击跳转前应先请求详情确认，或直接容忍 404。
 */
@Schema(description = "通知信息")
public record NotificationVO(

        @Schema(description = "通知 ID", example = "1")
        Long id,

        @Schema(description = "通知类型 1 关注 2 点赞 3 评论", example = "2")
        Integer type,

        @Schema(description = "触发者信息")
        UserBriefVO actor,

        @Schema(description = "相关动态 ID，可能为 null 或指向已删除的动态", example = "1")
        Long postId,

        @Schema(description = "相关评论 ID", example = "5")
        Long commentId,

        @Schema(description = "内容快照（评论摘要等），关注/点赞类型为空串")
        String content,

        @Schema(description = "是否已读", example = "false")
        Boolean isRead,

        @Schema(description = "通知时间")
        LocalDateTime createTime
) {
}
