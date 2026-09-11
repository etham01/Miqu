package com.miqu.common.enums;

import lombok.Getter;

/** 举报处理状态。对应 {@code report.status}。 */
@Getter
public enum ReportStatusEnum {

    PENDING(0, "待处理"),
    HANDLED(1, "已处理"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String desc;

    ReportStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isPending(Integer code) {
        return code != null && code == PENDING.code;
    }
}
