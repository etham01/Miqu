package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 私信会话（1:1）。对应 {@code conversation} 表。
 *
 * <p>继承 {@link BaseEntityNoDelete}：表结构里**没有 {@code deleted} 列**。
 *
 * <p><b>不变式：{@code user1Id < user2Id}</b>（DB 层有 CHECK 约束兜底）。
 * 配合唯一键 {@code uk_users(user1_id, user2_id)}，使 (A,B) 与 (B,A) 天然是同一条记录，
 * 无需在 Service 层额外去重。任何构造会话的地方都必须先做 min/max 规整。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("conversation")
public class Conversation extends BaseEntityNoDelete {

    /** 参与者中 ID 较小的一方。 */
    private Long user1Id;

    /** 参与者中 ID 较大的一方。 */
    private Long user2Id;

    private Long lastMessageId;

    /** 最后一条消息的预览，截断自 content。 */
    private String lastMessagePreview;

    private LocalDateTime lastMessageTime;

    /**
     * user1 的未读数。
     * 这是**权威值**（用于会话列表与角标），{@code message.is_read} 用于详情页渲染；
     * 任何修改未读的操作必须同事务更新两者。
     */
    private Integer user1Unread;

    /** user2 的未读数（权威值）。 */
    private Integer user2Unread;

    /** 返回给定用户在本会话中对应的未读数字段名，供 Service 分支使用。 */
    public boolean isMember(Long userId) {
        return userId != null && (userId.equals(user1Id) || userId.equals(user2Id));
    }

    /** 取对方的用户 ID。 */
    public Long partnerOf(Long userId) {
        return user1Id.equals(userId) ? user2Id : user1Id;
    }
}
