package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 通知。对应 {@code notification} 表。
 *
 * <p>type 取值见 {@link com.miqu.common.enums.NotificationTypeEnum}，
 * 一期只产生 关注(1) / 点赞(2) / 评论(3) 三类；私信不写通知表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    /** 接收者。 */
    private Long userId;

    /** 通知类型。 */
    private Integer type;

    /** 触发者。 */
    private Long actorId;

    private Long postId;

    private Long commentId;

    /** 预留字段，一期不使用。 */
    private Long messageId;

    /** 内容快照，防止关联的动态/评论被删后通知页出现断链。 */
    private String content;

    private Integer isRead;
}
