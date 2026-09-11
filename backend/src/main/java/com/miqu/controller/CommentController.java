package com.miqu.controller;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.request.CommentCreateRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.CommentService;
import com.miqu.vo.CommentVO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口。
 *
 * <p>评论的"创建/查询"挂在动态路径下（资源从属关系清晰），
 * "删除"按评论 ID 直接定位。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "评论", description = "发表、查看、删除评论")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{id}/comments")
    @Operation(summary = "发表评论", description = "内容不能为空，长度 ≤500 字符。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发表成功"),
            @ApiResponse(responseCode = "400", description = "评论内容为空或超过 500 字符"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "动态不存在或已被删除")
    })
    public Result<CommentVO> create(@CurrentUser Long userId,
                                    @Parameter(description = "动态 ID") @PathVariable Long id,
                                    @Valid @RequestBody CommentCreateRequest request) {
        return Result.ok(commentService.create(userId, id, request));
    }

    @GetMapping("/posts/{id}/comments")
    @Operation(summary = "评论列表",
            description = "按时间**正序**返回（先发的在前），读起来像对话。游客可访问。")
    @ApiResponse(responseCode = "404", description = "动态不存在或已被删除")
    public Result<PageResult<CommentVO>> list(@CurrentUser Long userId,
                                              @Parameter(description = "动态 ID") @PathVariable Long id,
                                              @Valid PageQuery query) {
        return Result.ok(commentService.list(userId, id, query));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "删除评论", description = "只有评论作者本人或管理员可以删除。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "不是作者且不是管理员"),
            @ApiResponse(responseCode = "404", description = "评论不存在或已被删除")
    })
    public Result<Void> delete(@CurrentUser Long userId,
                               @Parameter(description = "评论 ID") @PathVariable Long id) {
        commentService.delete(userId, id);
        return Result.ok();
    }
}
