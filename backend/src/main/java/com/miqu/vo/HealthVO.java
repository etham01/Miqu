package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 健康检查结果。
 *
 * <p>{@code database} 字段存在的意义：应用进程活着**不等于**能提供服务。
 * 如果只报告进程存活，就会出现"健康检查 UP，但每个业务请求都 500"这种
 * 极具误导性的状态——数据库挂掉时正是如此。
 */
@Schema(description = "健康检查结果")
public record HealthVO(

        @Schema(description = "整体状态：UP 正常 / DOWN 异常", example = "UP")
        String status,

        @Schema(description = "应用名", example = "miqu")
        String application,

        @Schema(description = "数据库连通性：UP 正常 / DOWN 不可用", example = "UP")
        String database
) {
}
