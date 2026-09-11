package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 用户提交举报后的回执。 */
@Schema(description = "举报提交结果")
public record ReportVO(

        @Schema(description = "举报 ID", example = "6")
        Long id,

        @Schema(description = "目标类型：1 用户 2 动态 3 评论", example = "2")
        Integer targetType,

        @Schema(description = "目标 ID", example = "11")
        Long targetId,

        @Schema(description = "提交时间")
        LocalDateTime createTime
) {
}
