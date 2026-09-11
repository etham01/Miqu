package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户搜索参数")
public class UserSearchQuery extends PageQuery {

    /** 关键词长度上限。过短的关键词会退化成全表扫描，过长没有实际意义。 */
    public static final int KEYWORD_MAX = 32;

    @Schema(description = "关键词，同时匹配昵称与用户名", example = "张")
    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = KEYWORD_MAX, message = "搜索关键词不能超过 32 个字符")
    private String keyword;
}
