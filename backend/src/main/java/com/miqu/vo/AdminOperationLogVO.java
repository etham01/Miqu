package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理员操作日志")
public record AdminOperationLogVO(

        @Schema(description = "日志 ID", example = "1")
        Long id,

        @Schema(description = "操作人信息")
        UserBriefVO admin,

        @Schema(description = "操作类型", example = "DELETE_POST")
        String operationType,

        @Schema(description = "目标类型：1 用户 2 动态 3 评论 4 举报", example = "2")
        Integer targetType,

        @Schema(description = "目标 ID", example = "12")
        Long targetId,

        @Schema(description = "操作摘要（不含任何敏感信息）")
        String detail,

        @Schema(description = "操作 IP", example = "127.0.0.1")
        String ip,

        @Schema(description = "操作时间")
        LocalDateTime createTime
) {
}
