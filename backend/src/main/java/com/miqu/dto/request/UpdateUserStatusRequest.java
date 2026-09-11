package com.miqu.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "修改用户状态请求")
public record UpdateUserStatusRequest(

        @Schema(description = "目标状态：1 正常 0 禁用", example = "0")
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态只能是 0（禁用）或 1（正常）")
        @Max(value = 1, message = "状态只能是 0（禁用）或 1（正常）")
        Integer status
) {
}
