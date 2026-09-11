package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台动态查询参数")
public class AdminPostQuery extends PageQuery {

    @Schema(description = "按作者 ID 过滤", example = "2")
    private Long userId;

    @Schema(description = "关键词，匹配动态内容", example = "测试")
    @Size(max = 32, message = "搜索关键词不能超过 32 个字符")
    private String keyword;

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
