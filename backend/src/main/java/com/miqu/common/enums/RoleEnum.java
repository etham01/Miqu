package com.miqu.common.enums;

import lombok.Getter;

/** 用户角色。对应 {@code user.role}。 */
@Getter
public enum RoleEnum {

    USER(1, "普通用户"),
    ADMIN(2, "管理员");

    private final int code;
    private final String desc;

    RoleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isAdmin(Integer code) {
        return code != null && code == ADMIN.code;
    }
}
