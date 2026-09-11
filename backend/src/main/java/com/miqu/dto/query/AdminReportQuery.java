package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台举报查询参数")
public class AdminReportQuery extends PageQuery {

    @Schema(description = "按处理状态过滤：0 待处理 1 已处理 2 已驳回；不传表示全部", example = "0")
    @Min(value = 0, message = "处理状态只能是 0、1 或 2")
    @Max(value = 2, message = "处理状态只能是 0、1 或 2")
    private Integer status;
}
