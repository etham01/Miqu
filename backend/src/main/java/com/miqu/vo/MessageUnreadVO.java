package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 私信未读数。
 *
 * <p>{@code total} 声明为**基本类型** long 而不是包装类型 Long：
 * JacksonConfig 把 {@code Long} 序列化成字符串（防前端精度丢失），
 * 但计数不是 ID，应当是数字。用 {@code Map<String, Long>} 返回会因自动装箱
 * 变成包装类型，前端拿到的就是 {@code "3"} 而不是 {@code 3}——
 * 与 {@link NotificationUnreadVO} 的返回类型也不一致。
 */
@Schema(description = "私信未读数")
public record MessageUnreadVO(

        @Schema(description = "未读私信总条数", example = "3")
        long total
) {
}
