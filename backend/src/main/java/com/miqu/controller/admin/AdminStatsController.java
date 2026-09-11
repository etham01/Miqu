package com.miqu.controller.admin;

import com.miqu.common.Result;
import com.miqu.security.RequireAdmin;
import com.miqu.service.AdminService;
import com.miqu.vo.AdminStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台数据统计。
 *
 * <p>{@code /api/admin/**} 不在免登录白名单里，因此未登录会被拦截返回 401；
 * 登录了但不是管理员则由 {@link RequireAdmin} 拦下返回 403 —— 两种失败原因可区分。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 统计", description = "首页数据概览")
public class AdminStatsController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(summary = "数据统计",
            description = "用户/动态/评论总数、今日新增，以及待处理举报数（用于后台首页待办提醒）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "不是管理员")
    })
    public Result<AdminStatsVO> stats() {
        return Result.ok(adminService.stats());
    }
}
