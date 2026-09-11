package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台用户查询参数")
public class AdminUserQuery extends PageQuery {

    @Schema(description = "关键词，匹配用户名或昵称", example = "test")
    @Size(max = 32, message = "搜索关键词不能超过 32 个字符")
    private String keyword;

    @Schema(description = "按状态过滤：1 正常 0 禁用；不传表示全部", example = "1")
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
