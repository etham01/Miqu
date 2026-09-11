package com.miqu.common.enums;

import lombok.Getter;

/** 用户状态。对应 {@code user.status}。 */
@Getter
public enum UserStatusEnum {

    DISABLED(0, "已禁用"),
    NORMAL(1, "正常");

    private final int code;
    private final String desc;

    UserStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isNormal(Integer code) {
        return code != null && code == NORMAL.code;
    }
}
