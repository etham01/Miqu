package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 管理员操作日志。对应 {@code admin_operation_log} 表。
 *
 * <p>追加写、不修改、不删除。{@code detail} 中禁止写入密码、JWT 等敏感信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("admin_operation_log")
public class AdminOperationLog extends BaseCreateEntity {

    private Long adminId;

    /** 操作类型，如 DELETE_POST / DISABLE_USER。 */
    private String operationType;

    /** 见 {@link com.miqu.common.enums.TargetTypeEnum}。 */
    private Integer targetType;

    private Long targetId;

    private String detail;

    private String ip;
}
