package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理后台的举报视图。
 *
 * <p>{@code targetPreview} 是服务端按 {@code targetType} 批量解析出来的目标摘要
 * （动态内容前若干字 / 评论内容 / 用户昵称）。
 *
 * <p>为什么要在服务端做：{@code target_id} 是多态外键，前端拿到
 * {@code (targetType=2, targetId=11)} 无法直接展示"被举报的是什么"，
 * 必须再发一次请求；而列表里逐条查就是 N+1。服务端按类型分组批量查询最划算。
 */
@Schema(description = "管理后台举报信息")
public record AdminReportVO(

        @Schema(description = "举报 ID", example = "1")
        Long id,

        @Schema(description = "举报人信息")
        UserBriefVO reporter,

        @Schema(description = "目标类型：1 用户 2 动态 3 评论", example = "2")
        Integer targetType,

        @Schema(description = "目标 ID", example = "11")
        Long targetId,

        @Schema(description = "被举报对象的摘要；目标已被删除时为提示文案")
        String targetPreview,

        @Schema(description = "举报原因：1 垃圾广告 2 辱骂骚扰 3 色情低俗 4 违法违规 5 其他", example = "1")
        Integer reasonType,

        @Schema(description = "补充说明")
        String reasonDetail,

        @Schema(description = "处理状态：0 待处理 1 已处理 2 已驳回", example = "0")
        Integer status,

        @Schema(description = "处理人信息；未处理时为 null")
        UserBriefVO handler,

        @Schema(description = "处理备注")
        String handleRemark,

        @Schema(description = "处理时间")
        LocalDateTime handleTime,

        @Schema(description = "举报时间")
        LocalDateTime createTime
) {
}
