package com.miqu.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页响应。
 *
 * <p>刻意不直接返回 MyBatis-Plus 的 {@code IPage}：一是屏蔽 ORM 字段命名
 * （records/current/pages），避免前端被 ORM 细节绑架；二是将来更换 ORM 时前端零改动；
 * 三是 {@code hasNext} 是列表页做无限滚动的必需字段，IPage 默认不提供。
 *
 * <p>注意：total/page/size 使用基本类型 long，序列化后是数字而非字符串
 * （Jackson 只对包装类型 Long 配置了转字符串，见 {@code JacksonConfig}）。
 */
@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "当前页数据")
        List<T> list,

        @Schema(description = "总记录数", example = "128")
        long total,

        @Schema(description = "当前页码，从 1 开始", example = "1")
        long page,

        @Schema(description = "每页条数", example = "10")
        long size,

        @Schema(description = "是否存在下一页", example = "true")
        boolean hasNext
) {

    public static <T> PageResult<T> of(long total, long page, long size, List<T> list) {
        List<T> safeList = list == null ? new ArrayList<>() : list;
        return new PageResult<>(safeList, total, page, size, page * size < total);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(new ArrayList<>(), 0L, page, size, false);
    }
}
