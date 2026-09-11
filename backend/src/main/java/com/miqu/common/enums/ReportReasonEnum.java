package com.miqu.common.enums;

import lombok.Getter;

import java.util.Arrays;

/** 举报原因。对应 {@code report.reason_type}。 */
@Getter
public enum ReportReasonEnum {

    SPAM(1, "垃圾广告"),
    ABUSE(2, "辱骂骚扰"),
    PORNOGRAPHY(3, "色情低俗"),
    ILLEGAL(4, "违法违规"),
    OTHER(5, "其他");

    private final int code;
    private final String desc;

    ReportReasonEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isValid(Integer code) {
        return code != null && Arrays.stream(values()).anyMatch(e -> e.code == code);
    }
}
