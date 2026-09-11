package com.miqu.common.enums;

import lombok.Getter;

/**
 * 操作/举报目标类型。
 *
 * <p>该字段是**多态外键**：target_id 指向 user / post / comment 中的一张表，
 * 因此无法建物理外键，引用完整性由 Service 层按 type 分支校验。
 */
@Getter
public enum TargetTypeEnum {

    USER(1, "用户"),
    POST(2, "动态"),
    COMMENT(3, "评论"),
    REPORT(4, "举报");

    private final int code;
    private final String desc;

    TargetTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static boolean isValid(Integer code) {
        return code != null && code >= USER.code && code <= REPORT.code;
    }
}
