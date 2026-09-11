package com.miqu.controller.admin;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.AdminPostQuery;
import com.miqu.security.CurrentUser;
import com.miqu.security.RequireAdmin;
import com.miqu.service.AdminService;
import com.miqu.vo.AdminPostVO;
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
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 动态", description = "动态列表与删除")
public class AdminPostController {

    private final AdminService adminService;

    @GetMapping
    @Operation(summary = "动态列表",
            description = "支持按作者 ID 与内容关键词过滤，返回图片与作者信息。只列出未删除的动态。")
    public Result<PageResult<AdminPostVO>> list(@Valid AdminPostQuery query) {
        return Result.ok(adminService.listPosts(query));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除动态",
            description = """
                    与作者自删走同一套 Service：逻辑删除动态、逻辑删除其评论、
                    物理删除图片与点赞，并回滚作者动态数与相关计数，同时清理挂在该动态上的通知。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "动态不存在或已被删除")
    })
    public Result<Void> delete(@CurrentUser Long adminId,
                               @Parameter(description = "动态 ID") @PathVariable Long id) {
        adminService.deletePost(adminId, id);
        return Result.ok();
    }
}
