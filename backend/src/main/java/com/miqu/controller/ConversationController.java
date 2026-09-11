package com.miqu.controller;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.MessageQuery;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.request.CreateConversationRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.ConversationService;
import com.miqu.service.MessageService;
import com.miqu.vo.ConversationVO;
import com.miqu.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话接口。
 *
 * <p>一期采用 **REST + 轮询**，不引入 WebSocket：
 * 需求明确"不要因为追求技术而导致整个项目复杂化"。
 * 前端建议 3~5 秒轮询一次未读数与当前打开的会话。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "会话", description = "私信会话列表与聊天记录")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping
    @Operation(summary = "会话列表",
            description = """
                    按最后消息时间倒序。**只返回已经有消息的会话**——
                    通过 `POST /api/conversations` 建出来但还没发过消息的空会话不会出现，
                    避免列表里出现空白条目。

                    `unreadCount` 取的是会话表上的权威未读数，不是实时扫消息表算出来的。
                    """)
    public Result<PageResult<ConversationVO>> list(@CurrentUser Long userId,
                                                   @Valid PageQuery query) {
        return Result.ok(conversationService.list(userId, query));
    }

    @PostMapping
    @Operation(summary = "获取或创建与某人的会话",
            description = """
                    幂等：已存在则直接返回原会话，不会重复创建。

                    服务端会把两个用户 ID 规整成 (较小, 较大) 后再查找，
                    因此 (A,B) 与 (B,A) 一定命中同一条记录。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "400", description = "不能与自己发起会话"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "对方用户不存在或已注销"),
            @ApiResponse(responseCode = "423", description = "对方账号已被禁用")
    })
    public Result<ConversationVO> open(@CurrentUser Long userId,
                                       @Valid @RequestBody CreateConversationRequest request) {
        return Result.ok(conversationService.openConversation(userId, request.targetUserId()));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "聊天记录（游标分页）",
            description = """
                    返回结果按**时间正序**排列，前端可直接从上往下渲染。

                    分页用游标而非 offset：首次加载不传 `beforeId`，
                    之后传上一页**最早一条**消息的 id。聊天记录会不断从头部新增，
                    用 offset 翻历史消息时会被新消息挤得错位。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "不是该会话的参与者"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public Result<List<MessageVO>> messages(@CurrentUser Long userId,
                                            @Parameter(description = "会话 ID") @PathVariable Long id,
                                            @Valid MessageQuery query) {
        return Result.ok(messageService.listMessages(userId, id, query));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记该会话已读",
            description = "把我收到的未读消息全部置为已读，同时把会话上的权威未读数清零。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "不是该会话的参与者"),
            @ApiResponse(responseCode = "404", description = "会话不存在")
    })
    public Result<Void> markRead(@CurrentUser Long userId,
                                 @Parameter(description = "会话 ID") @PathVariable Long id) {
        conversationService.markRead(userId, id);
        return Result.ok();
    }
}
