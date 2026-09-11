package com.miqu.controller;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.query.UserSearchQuery;
import com.miqu.dto.request.ChangePasswordRequest;
import com.miqu.dto.request.UpdateAvatarRequest;
import com.miqu.dto.request.UpdateProfileRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.FollowService;
import com.miqu.service.PostService;
import com.miqu.service.UserService;
import com.miqu.vo.FollowResultVO;
import com.miqu.vo.PostVO;
import com.miqu.vo.UserFollowVO;
import com.miqu.vo.UserProfileVO;
import com.miqu.vo.UserSearchVO;
import com.miqu.vo.UserVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 *
 * <p>路由说明：{@code /me} 系列是字面路径，优先级高于 {@code /{id}} 模板，
 * 因此 {@code GET /api/users/me} 不会被 {@code GET /api/users/{id}} 抢走。
 *
 * <p>{@code /me/**} 要求登录；{@code /{id}} 系列对游客开放，
 * 此时关注状态字段一律为 false。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户", description = "用户信息、资料维护、关注关系")
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final PostService postService;

    // ==================== 当前用户 ====================

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录或 Token 失效"),
            @ApiResponse(responseCode = "423", description = "账号已被禁用")
    })
    public Result<UserVO> getCurrentUser(@CurrentUser Long userId) {
        return Result.ok(userService.getCurrentUser(userId));
    }

    @PutMapping("/me")
    @Operation(summary = "修改个人资料",
            description = "仅支持修改昵称、性别、生日、简介。字段为 null 表示不修改。用户名与邮箱不可修改。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功，返回更新后的用户信息"),
            @ApiResponse(responseCode = "400", description = "参数校验失败"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<UserVO> updateProfile(@CurrentUser Long userId,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/password")
    @Operation(summary = "修改密码", description = "需要提供原密码；新密码不能与原密码相同。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功"),
            @ApiResponse(responseCode = "400", description = "原密码错误，或新密码长度不符、与原密码相同"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<Void> changePassword(@CurrentUser Long userId,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return Result.ok();
    }

    @PutMapping("/me/avatar")
    @Operation(summary = "修改头像",
            description = "先调用 `POST /api/files/image` 上传图片拿到 URL，再把 URL 提交到本接口。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功，返回更新后的用户信息"),
            @ApiResponse(responseCode = "400", description = "头像地址为空或超长"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<UserVO> updateAvatar(@CurrentUser Long userId,
                                       @Valid @RequestBody UpdateAvatarRequest request) {
        return Result.ok(userService.updateAvatar(userId, request));
    }

    @GetMapping("/me/following")
    @Operation(summary = "我的关注列表")
    public Result<PageResult<UserFollowVO>> myFollowing(@CurrentUser Long userId,
                                                        @Valid PageQuery query) {
        return Result.ok(followService.listFollowing(userId, userId, query));
    }

    @GetMapping("/me/followers")
    @Operation(summary = "我的粉丝列表",
            description = "每项带 `followedByMe`，前端据此显示「回关」按钮。")
    public Result<PageResult<UserFollowVO>> myFollowers(@CurrentUser Long userId,
                                                        @Valid PageQuery query) {
        return Result.ok(followService.listFollowers(userId, userId, query));
    }

    // ==================== 其他用户 ====================

    @GetMapping("/search")
    @Operation(summary = "搜索用户",
            description = """
                    同时匹配**昵称**与**用户名**，使用 MySQL `LIKE`，未引入 Elasticsearch。

                    关键词里的 `%` 与 `_` 会被转义——否则搜一个 `%` 就会返回全部用户。
                    结果按粉丝数倒序，游客可访问（此时 `followedByMe` 恒为 false）。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "400", description = "关键词为空或超过 32 个字符")
    })
    public Result<PageResult<UserSearchVO>> search(@CurrentUser Long currentUserId,
                                                   @Valid UserSearchQuery query) {
        return Result.ok(userService.search(currentUserId, query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "用户主页信息",
            description = """
                    游客可访问，此时 `followedByMe` / `followingMe` / `mutual` 均为 false。

                    被**禁用**的用户主页仍可浏览（只是无法与之互动），
                    已**注销**的用户返回 404。
                    """)
    @ApiResponse(responseCode = "404", description = "用户不存在或已注销")
    public Result<UserProfileVO> getProfile(@CurrentUser Long currentUserId,
                                            @Parameter(description = "用户 ID") @PathVariable Long id) {
        return Result.ok(userService.getProfile(currentUserId, id));
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "某用户发布的动态")
    @ApiResponse(responseCode = "404", description = "用户不存在或已注销")
    public Result<PageResult<PostVO>> userPosts(@CurrentUser Long currentUserId,
                                                @Parameter(description = "用户 ID") @PathVariable Long id,
                                                @Valid PageQuery query) {
        return Result.ok(postService.listByUser(currentUserId, id, query));
    }

    @GetMapping("/{id}/following")
    @Operation(summary = "某用户关注的人")
    @ApiResponse(responseCode = "404", description = "用户不存在或已注销")
    public Result<PageResult<UserFollowVO>> following(@CurrentUser Long currentUserId,
                                                      @Parameter(description = "用户 ID") @PathVariable Long id,
                                                      @Valid PageQuery query) {
        return Result.ok(followService.listFollowing(currentUserId, id, query));
    }

    @GetMapping("/{id}/followers")
    @Operation(summary = "某用户的粉丝")
    @ApiResponse(responseCode = "404", description = "用户不存在或已注销")
    public Result<PageResult<UserFollowVO>> followers(@CurrentUser Long currentUserId,
                                                      @Parameter(description = "用户 ID") @PathVariable Long id,
                                                      @Valid PageQuery query) {
        return Result.ok(followService.listFollowers(currentUserId, id, query));
    }

    // ==================== 关注 ====================

    @PostMapping("/{id}/follow")
    @Operation(summary = "关注用户")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "关注成功，返回最新粉丝数"),
            @ApiResponse(responseCode = "400", description = "不能关注自己"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "目标用户不存在或已注销"),
            @ApiResponse(responseCode = "409", description = "已经关注过该用户"),
            @ApiResponse(responseCode = "423", description = "目标用户已被禁用")
    })
    public Result<FollowResultVO> follow(@CurrentUser Long userId,
                                         @Parameter(description = "被关注者 ID") @PathVariable Long id) {
        return Result.ok(followService.follow(userId, id));
    }

    @DeleteMapping("/{id}/follow")
    @Operation(summary = "取消关注",
            description = """
                    本来就没关注时返回 **404**（而不是静默成功），语义明确、便于测试断言。

                    连带要求：前端关注按钮需在请求进行中禁用，否则连点两次会弹出错误提示。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取消成功，返回最新粉丝数"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "用户不存在，或尚未关注该用户")
    })
    public Result<FollowResultVO> unfollow(@CurrentUser Long userId,
                                           @Parameter(description = "被取消关注者 ID") @PathVariable Long id) {
        return Result.ok(followService.unfollow(userId, id));
    }
}
