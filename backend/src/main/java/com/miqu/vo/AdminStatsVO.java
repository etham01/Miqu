package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台首页统计。
 *
 * <p>全部字段使用**基本类型** long，保证序列化后是数字而不是字符串
 * （JacksonConfig 只把包装类型 Long 转成字符串）。
 */
@Schema(description = "管理后台数据统计")
public record AdminStatsVO(

        @Schema(description = "用户总数", example = "21")
        long userTotal,

        @Schema(description = "动态总数", example = "40")
        long postTotal,

        @Schema(description = "评论总数", example = "111")
        long commentTotal,

        @Schema(description = "今日新增用户", example = "0")
        long todayNewUser,

        @Schema(description = "今日新增动态", example = "0")
        long todayNewPost,

        @Schema(description = "今日新增评论", example = "0")
        long todayNewComment,

        @Schema(description = "待处理举报数，用于后台首页的待办提醒", example = "3")
        long pendingReportTotal
) {
}
