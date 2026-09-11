package com.miqu.common.enums;

import lombok.Getter;

import java.util.Arrays;

/** 性别。对应 {@code user.gender}。 */
@Getter
public enum GenderEnum {

    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final int code;
    private final String desc;

    GenderEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isValid(Integer code) {
        return code != null && Arrays.stream(values()).anyMatch(e -> e.code == code);
    }

    public static GenderEnum of(Integer code) {
        return Arrays.stream(values())
                .filter(e -> e.code == (code == null ? -1 : code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
