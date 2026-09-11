package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理后台评论信息")
public record AdminCommentVO(

        @Schema(description = "评论 ID", example = "1")
        Long id,

        @Schema(description = "所属动态 ID", example = "1")
        Long postId,

        @Schema(description = "评论内容")
        String content,

        @Schema(description = "评论者信息")
        UserBriefVO author,

        @Schema(description = "评论时间")
        LocalDateTime createTime
) {
}
