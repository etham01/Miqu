package com.miqu.controller.admin;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.AdminCommentQuery;
import com.miqu.security.CurrentUser;
import com.miqu.security.RequireAdmin;
import com.miqu.service.AdminService;
import com.miqu.vo.AdminCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/comments")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 评论", description = "评论列表与删除")
public class AdminCommentController {

    private final AdminService adminService;

    @GetMapping
    @Operation(summary = "评论列表", description = "支持按所属动态 ID、评论者 ID 与内容关键词过滤。")
    public Result<PageResult<AdminCommentVO>> list(@Valid AdminCommentQuery query) {
        return Result.ok(adminService.listComments(query));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除评论",
            description = "逻辑删除评论，并在所属动态未被删除时回滚其评论数。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "评论不存在或已被删除")
    })
    public Result<Void> delete(@CurrentUser Long adminId,
                               @Parameter(description = "评论 ID") @PathVariable Long id) {
        adminService.deleteComment(adminId, id);
        return Result.ok();
    }
}
