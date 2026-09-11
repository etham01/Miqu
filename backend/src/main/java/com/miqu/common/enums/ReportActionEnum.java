package com.miqu.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 管理员处理举报时可同时执行的处置动作。
 *
 * <p>把"处理举报"与"处置内容"合成一个请求，是因为这两件事在真实后台里
 * 永远是同一个动作的两半：管理员判定违规后顺手就把内容删了。
 * 分开成两个接口反而会留下"举报已处理但内容还在"的中间态。
 */
@Getter
public enum ReportActionEnum {

    /** 不做额外处置，只记录处理结论。 */
    NONE("不处置"),

    /** 删除被举报的动态。 */
    DELETE_POST("删除动态"),

    /** 删除被举报的评论。 */
    DELETE_COMMENT("删除评论"),

    /** 禁用被举报用户（仅当举报目标是用户时可用）。 */
    DISABLE_USER("禁用用户");

    private final String desc;

    ReportActionEnum(String desc) {
        this.desc = desc;
    }

    public static ReportActionEnum of(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        return Arrays.stream(values())
                .filter(e -> e.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的处置动作：" + name));
    }
}
