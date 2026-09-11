package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 举报。对应 {@code report} 表。
 *
 * <p>继承 {@link BaseEntityNoDelete}：表结构里**没有 {@code deleted} 列**——
 * 举报是处理流程记录，不做逻辑删除（否则"已处理的举报"会从后台列表里凭空消失）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("report")
public class Report extends BaseEntityNoDelete {

    private Long reporterId;

    /** 见 {@link com.miqu.common.enums.TargetTypeEnum}，1用户 2动态 3评论。 */
    private Integer targetType;

    /**
     * 目标 ID。多态外键，指向 user/post/comment 之一，
     * 无法建物理外键，存在性由 Service 层按 targetType 分支校验。
     */
    private Long targetId;

    /** 见 {@link com.miqu.common.enums.ReportReasonEnum}。 */
    private Integer reasonType;

    private String reasonDetail;

    /** 见 {@link com.miqu.common.enums.ReportStatusEnum}，0待处理 1已处理 2已驳回。 */
    private Integer status;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime handleTime;
}
