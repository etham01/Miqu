package com.miqu.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员处理举报。
 *
 * <p>{@code status=2}（驳回）时不允许同时执行处置动作——
 * 既判定未违规又删内容自相矛盾，Service 层会拦住这种组合。
 */
@Schema(description = "处理举报请求")
public record HandleReportRequest(

        @Schema(description = "处理结论：1 已处理（违规成立）2 已驳回（未违规）", example = "1")
        @NotNull(message = "处理结论不能为空")
        @Min(value = 1, message = "处理结论只能是 1（已处理）或 2（已驳回）")
        @Max(value = 2, message = "处理结论只能是 1（已处理）或 2（已驳回）")
        Integer status,

        @Schema(description = "处理备注", example = "已确认违规，删除该动态")
        @Size(max = 255, message = "处理备注不能超过 255 个字符")
        String handleRemark,

        @Schema(description = """
                同时执行的处置动作：NONE 不处置 / DELETE_POST 删除动态 /
                DELETE_COMMENT 删除评论 / DISABLE_USER 禁用用户。不传默认为 NONE。
                """, example = "DELETE_POST")
        String action
) {
}
