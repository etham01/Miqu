package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "提交举报请求")
public record CreateReportRequest(

        @Schema(description = "举报目标类型：1 用户 2 动态 3 评论", example = "2")
        @NotNull(message = "举报目标类型不能为空")
        @Min(value = 1, message = "举报目标类型只能是 1、2 或 3")
        @Max(value = 3, message = "举报目标类型只能是 1、2 或 3")
        Integer targetType,

        @Schema(description = "举报目标 ID", example = "11")
        @NotNull(message = "举报目标不能为空")
        Long targetId,

        @Schema(description = "举报原因：1 垃圾广告 2 辱骂骚扰 3 色情低俗 4 违法违规 5 其他", example = "1")
        @NotNull(message = "举报原因不能为空")
        @Min(value = 1, message = "举报原因取值不合法")
        @Max(value = 5, message = "举报原因取值不合法")
        Integer reasonType,

        @Schema(description = "补充说明", example = "疑似营销广告内容")
        @Size(max = BizConstants.REPORT_DETAIL_MAX, message = "补充说明不能超过 255 个字符")
        String reasonDetail
) {
}
