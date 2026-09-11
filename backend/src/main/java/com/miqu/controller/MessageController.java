package com.miqu.controller;

import com.miqu.common.Result;
import com.miqu.dto.request.MessageSendRequest;
import com.miqu.security.CurrentUser;
import com.miqu.service.MessageService;
import com.miqu.vo.MessageUnreadVO;
import com.miqu.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "私信", description = "发送私信与未读数")
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @Operation(summary = "发送私信",
            description = """
                    只需提供 `receiverId`，会话由服务端自动创建或复用，
                    客户端不需要先调 `POST /api/conversations`。

                    发送成功后，接收方的会话未读数 +1，会话的最后消息预览同步更新。
                    这两个动作在同一条 SQL 里原子完成，不会与并发消息互相覆盖。
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发送成功"),
            @ApiResponse(responseCode = "400", description = "内容为空、超过 1000 字符，或给自己发消息"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "404", description = "接收者不存在或已注销"),
            @ApiResponse(responseCode = "423", description = "接收者账号已被禁用")
    })
    public Result<MessageVO> send(@CurrentUser Long userId,
                                  @Valid @RequestBody MessageSendRequest request) {
        return Result.ok(messageService.send(userId, request));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "私信未读总数",
            description = """
                    用于导航栏角标。**私信不计入通知未读数**——
                    两者是各自独立的计数，避免同一件事在两个角标里重复出现。

                    返回的 `total` 是**数字**而不是字符串：接口中只有 id 字段被序列化为字符串
                    （防前端精度丢失），计数类字段一律是数字。
                    """)
    public Result<MessageUnreadVO> unreadCount(@CurrentUser Long userId) {
        return Result.ok(new MessageUnreadVO(messageService.unreadTotal(userId)));
    }
}
