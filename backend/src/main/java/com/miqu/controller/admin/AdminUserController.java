package com.miqu.controller.admin;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.AdminUserQuery;
import com.miqu.dto.request.UpdateUserStatusRequest;
import com.miqu.security.CurrentUser;
import com.miqu.security.RequireAdmin;
import com.miqu.service.AdminService;
import com.miqu.vo.AdminUserVO;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 用户", description = "用户列表、详情与状态管理")
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    @Operation(summary = "用户列表",
            description = "支持按关键词（用户名/昵称）与状态过滤。关键词中的 LIKE 通配符会被转义。")
    public Result<PageResult<AdminUserVO>> list(@Valid AdminUserQuery query) {
        return Result.ok(adminService.listUsers(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户详情")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在或已注销")
    })
    public Result<AdminUserVO> detail(@Parameter(description = "用户 ID") @PathVariable Long id) {
        return Result.ok(adminService.getUserDetail(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "禁用 / 解禁用户",
            description = """
                    `status`：1 正常，0 禁用。

                    两条保护：不能操作自己（400），不能禁用管理员账号（403）。

                    禁用**立即生效**：认证过滤器每次请求都会查库校验状态，
                    被禁用者手里已签发的 Token 会马上失效，而不是等 7 天过期。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "400", description = "状态取值非法，或试图操作自己"),
            @ApiResponse(responseCode = "403", description = "试图禁用管理员账号"),
            @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public Result<Void> updateStatus(@CurrentUser Long adminId,
                                     @Parameter(description = "用户 ID") @PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusRequest request) {
        adminService.updateUserStatus(adminId, id, request.status());
        return Result.ok();
    }
}
