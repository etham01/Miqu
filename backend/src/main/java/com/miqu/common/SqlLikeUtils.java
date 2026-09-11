package com.miqu.common;

/**
 * MySQL {@code LIKE} 关键词处理。
 *
 * <p>{@code %} 与 {@code _} 是 LIKE 的通配符。如果用户输入的关键词被直接拼进
 * {@code LIKE '%kw%'}，那么搜一个 {@code %} 就会命中**全部记录**——
 * 既是信息泄漏，也是典型的注入式输入。{@code \} 本身是转义符，也必须先转义，
 * 否则会破坏 SQL 里的 {@code ESCAPE} 子句。
 *
 * <p>配套的 SQL 写法（用参数绑定，不做字符串拼接）：
 * <pre>
 * wrapper.apply("nickname LIKE {0} ESCAPE '\\\\'", SqlLikeUtils.contains(keyword))
 * </pre>
 */
public final class SqlLikeUtils {

    /** 与 SQL 中 {@code ESCAPE '\\'} 声明的转义符保持一致。 */
    private static final String ESCAPE_CHAR = "\\";

    private SqlLikeUtils() {
    }

    /**
     * 转义关键词中的通配符，返回可用于 {@code LIKE} 的字面量。
     *
     * <p>注意转义顺序：必须先处理反斜杠，否则后续替换产生的反斜杠会被二次转义。
     */
    public static String escape(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword
                .replace(ESCAPE_CHAR, ESCAPE_CHAR + ESCAPE_CHAR)
                .replace("%", ESCAPE_CHAR + "%")
                .replace("_", ESCAPE_CHAR + "_");
    }

    /** 返回"包含"语义的匹配串：{@code %转义后的关键词%}。 */
    public static String contains(String keyword) {
        return "%" + escape(keyword) + "%";
    }
}
