package com.miqu.controller;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.query.PostQuery;
import com.miqu.dto.request.PostCreateRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.PostService;
import com.miqu.vo.LikeResultVO;
import com.miqu.vo.PostVO;
import com.miqu.vo.UserBriefVO;
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
 * 动态接口。
 *
 * <p>读接口（列表、详情、点赞列表）对游客开放；写接口一律要求登录。
 * 游客访问读接口时 {@code likedByMe} / {@code mine} 恒为 false，而不是报错。
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "动态", description = "发布、浏览、删除动态，以及点赞")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "发布动态",
            description = """
                    图片采用两阶段模式：先调用 `POST /api/files/image` 上传拿到 URL，
                    再把 URL 列表提交到本接口。

                    规则：内容与图片**不能同时为空**；内容 ≤1000 字；图片 ≤9 张；
                    图片 URL 必须来自本项目的上传接口，外链会被拒绝。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发布成功"),
            @ApiResponse(responseCode = "400", description = "内容与图片同时为空 / 内容超长 / 图片超过 9 张 / 图片地址非法"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<PostVO> create(@CurrentUser Long userId,
                                 @Valid @RequestBody PostCreateRequest request) {
        return Result.ok(postService.create(userId, request));
    }

    @GetMapping
    @Operation(summary = "首页动态流",
            description = """
                    `tab=latest`（默认）返回全站最新动态，游客可访问；
                    `tab=following` 返回我关注的人的动态，需要登录，未登录返回 401。

                    排序为 `create_time DESC, id DESC` —— 带主键兜底是为了避免
                    同一毫秒的记录在翻页时重复或丢失。
                    """)
    public Result<PageResult<PostVO>> list(@CurrentUser Long userId,
                                           @Valid PostQuery query) {
        return Result.ok(postService.listFeed(userId, query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "动态详情")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "404", description = "动态不存在或已被删除")
    })
    public Result<PostVO> detail(@CurrentUser Long userId,
                                 @Parameter(description = "动态 ID") @PathVariable Long id) {
        return Result.ok(postService.getDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除动态",
            description = "只有作者本人或管理员可以删除。删除时级联处理评论、图片与点赞。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "不是作者且不是管理员"),
            @ApiResponse(responseCode = "404", description = "动态不存在或已被删除")
    })
    public Result<Void> delete(@CurrentUser Long userId,
                               @Parameter(description = "动态 ID") @PathVariable Long id) {
        postService.delete(userId, id);
        return Result.ok();
    }

    @GetMapping("/{id}/likes")
    @Operation(summary = "点赞用户列表")
    public Result<PageResult<UserBriefVO>> likes(@Parameter(description = "动态 ID") @PathVariable Long id,
                                                 @Valid PageQuery query) {
        return Result.ok(postService.listLikes(id, query));
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "点赞", description = "重复点赞返回 409。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "点赞成功，返回最新点赞数"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "动态不存在"),
            @ApiResponse(responseCode = "409", description = "已经点赞过该动态")
    })
    public Result<LikeResultVO> like(@CurrentUser Long userId,
                                     @Parameter(description = "动态 ID") @PathVariable Long id) {
        return Result.ok(postService.like(userId, id));
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "取消点赞",
            description = """
                    本来就没点赞时返回 **404**（而不是静默成功），语义明确、便于测试断言。

                    连带要求：前端点赞按钮需在请求进行中禁用，否则连点两次会弹出错误提示。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取消成功，返回最新点赞数"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "动态不存在，或尚未点赞该动态")
    })
    public Result<LikeResultVO> unlike(@CurrentUser Long userId,
                                       @Parameter(description = "动态 ID") @PathVariable Long id) {
        return Result.ok(postService.unlike(userId, id));
    }
}
