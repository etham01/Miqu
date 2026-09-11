package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 私信消息。对应 {@code message} 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("message")
public class Message extends BaseEntity {

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    private String content;

    /** 0 未读 1 已读。未读数的权威来源是 conversation.*_unread。 */
    private Integer isRead;

    private LocalDateTime readTime;
}
