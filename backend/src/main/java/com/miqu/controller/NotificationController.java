package com.miqu.controller;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.NotificationQuery;
import com.miqu.security.CurrentUser;
import com.miqu.service.NotificationService;
import com.miqu.vo.NotificationUnreadVO;
import com.miqu.vo.NotificationVO;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口。
 *
 * <p>只有关注(1) / 点赞(2) / 评论(3) 三类。
 * **私信不产生通知**——它由 `/api/messages/unread-count` 与会话列表承载，
 * 避免同一件事在通知页和消息页重复出现。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "通知", description = "通知列表、未读数与已读标记")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "通知列表",
            description = """
                    按时间倒序，可按类型与已读状态过滤。

                    每条通知带 `content` 内容快照（如评论摘要）：关联的动态被删除后，
                    条目仍然可读，不会变成空白。注意 `postId` 指向的动态可能已经不存在。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<PageResult<NotificationVO>> list(@CurrentUser Long userId,
                                                   @Valid NotificationQuery query) {
        return Result.ok(notificationService.list(userId, query));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "通知未读数（按类型拆开）",
            description = "用于导航栏角标与通知页各 Tab 上的红点。")
    public Result<NotificationUnreadVO> unreadCount(@CurrentUser Long userId) {
        return Result.ok(notificationService.unreadCount(userId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "单条标记已读",
            description = "幂等：已读的通知再标记一次也返回成功。操作他人的通知返回 403。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "该通知不属于当前用户"),
            @ApiResponse(responseCode = "404", description = "通知不存在")
    })
    public Result<Void> markRead(@CurrentUser Long userId,
                                 @Parameter(description = "通知 ID") @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result<Void> markAllRead(@CurrentUser Long userId) {
        notificationService.markAllRead(userId);
        return Result.ok();
    }
}
