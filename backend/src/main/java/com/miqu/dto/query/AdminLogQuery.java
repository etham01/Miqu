package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理后台操作日志查询参数")
public class AdminLogQuery extends PageQuery {

    @Schema(description = "按操作管理员 ID 过滤", example = "1")
    private Long adminId;

    @Schema(description = "按操作类型过滤", example = "DELETE_POST")
    private String operationType;
}
