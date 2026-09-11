package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "评论信息")
public record CommentVO(

        @Schema(description = "评论 ID", example = "1")
        Long id,

        @Schema(description = "所属动态 ID", example = "1")
        Long postId,

        @Schema(description = "评论内容")
        String content,

        @Schema(description = "当前登录用户是否为评论作者", example = "false")
        Boolean mine,

        @Schema(description = "评论者信息")
        UserBriefVO author,

        @Schema(description = "评论时间")
        LocalDateTime createTime
) {
}
