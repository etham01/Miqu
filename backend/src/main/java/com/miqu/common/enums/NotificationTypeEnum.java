package com.miqu.common.enums;

import lombok.Getter;

/**
 * 通知类型。对应 {@code notification.type}。
 *
 * <p>注意：{@link #MESSAGE} 为**保留值，一期不使用**。
 * 已决策私信不写通知表，避免通知页与会话未读数重复表达同一件事。
 */
@Getter
public enum NotificationTypeEnum {

    FOLLOW(1, "关注"),
    LIKE(2, "点赞"),
    COMMENT(3, "评论"),

    /** 保留值，一期不产生该类型的通知。 */
    @Deprecated
    MESSAGE(4, "私信");

    private final int code;
    private final String desc;

    NotificationTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isValid(Integer code) {
        return code != null && (code == FOLLOW.code || code == LIKE.code || code == COMMENT.code);
    }
}
