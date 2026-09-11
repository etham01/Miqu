package com.miqu.controller;

import com.miqu.common.Result;
import com.miqu.dto.request.CreateReportRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.ReportService;
import com.miqu.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "举报", description = "提交举报（处理由管理后台完成）")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "提交举报",
            description = """
                    举报对象由 `targetType` + `targetId` 指定：
                    1 用户 / 2 动态 / 3 评论。

                    规则：不能举报自己的内容；同一目标不能重复举报。
                    举报进入待处理队列，由管理员在后台处理。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "提交成功"),
            @ApiResponse(responseCode = "400", description = "目标类型/原因取值非法，或举报了自己的内容"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "举报目标不存在"),
            @ApiResponse(responseCode = "409", description = "已经举报过该内容")
    })
    public Result<ReportVO> create(@CurrentUser Long userId,
                                   @Valid @RequestBody CreateReportRequest request) {
        return Result.ok(reportService.create(userId, request));
    }
}
