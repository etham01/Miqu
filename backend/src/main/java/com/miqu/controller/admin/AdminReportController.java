package com.miqu.controller.admin;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.AdminReportQuery;
import com.miqu.dto.request.HandleReportRequest;
import com.miqu.security.CurrentUser;
import com.miqu.security.RequireAdmin;
import com.miqu.service.ReportService;
import com.miqu.vo.AdminReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 举报", description = "举报列表与处理")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    @Operation(summary = "举报列表",
            description = """
                    可按处理状态过滤（0 待处理 / 1 已处理 / 2 已驳回）。

                    每条记录带 `targetPreview`：服务端按 `targetType` 批量解析出的
                    被举报对象摘要（动态内容 / 评论内容 / 用户昵称）。
                    因为 target_id 是多态外键，前端单看 ID 无法展示"被举报的是什么"。
                    目标已被删除时该字段为提示文案。
                    """)
    public Result<PageResult<AdminReportVO>> list(@Valid AdminReportQuery query) {
        return Result.ok(reportService.listForAdmin(query));
    }

    @PutMapping("/{id}/handle")
    @Operation(summary = "处理举报",
            description = """
                    `status`：1 已处理（违规成立）/ 2 已驳回（未违规）。
                    已处理过的举报不能重复处理（409），避免处置动作被执行两次。

                    可选 `action` 同时执行处置：
                    - `NONE` 不处置（默认）
                    - `DELETE_POST` 删除被举报动态
                    - `DELETE_COMMENT` 删除被举报评论
                    - `DISABLE_USER` 禁用被举报用户

                    约束：处置动作必须与举报目标类型匹配（400）；
                    驳回时不允许同时处置（400）——既判定未违规又删内容自相矛盾。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "处理成功"),
            @ApiResponse(responseCode = "400", description = "状态或动作取值非法、动作与目标类型不匹配、驳回时带处置动作"),
            @ApiResponse(responseCode = "404", description = "举报不存在"),
            @ApiResponse(responseCode = "409", description = "该举报已被处理")
    })
    public Result<Void> handle(@CurrentUser Long adminId,
                               @Parameter(description = "举报 ID") @PathVariable Long id,
                               @Valid @RequestBody HandleReportRequest request) {
        reportService.handle(adminId, id, request);
        return Result.ok();
    }
}
