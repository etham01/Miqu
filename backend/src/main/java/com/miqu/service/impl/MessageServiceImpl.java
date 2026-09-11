package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizConstants;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.dto.query.MessageQuery;
import com.miqu.dto.request.MessageSendRequest;
import com.miqu.entity.Conversation;
import com.miqu.entity.Message;
import com.miqu.mapper.ConversationMapper;
import com.miqu.mapper.MessageMapper;
import com.miqu.service.ConversationService;
import com.miqu.service.MessageService;
import com.miqu.service.UserService;
import com.miqu.service.support.FollowStatusLoader;
import com.miqu.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationService conversationService;
    private final UserService userService;
    private final FollowStatusLoader followStatusLoader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO send(Long userId, MessageSendRequest request) {
        userService.requireActiveUser(userId);

        Long receiverId = request.receiverId();
        if (userId.equals(receiverId)) {
            throw BizException.of(ErrorCode.CANNOT_MESSAGE_SELF);
        }
        // 给被禁用的用户发私信没有意义，且会积累永远读不到的消息
        userService.requireActiveUser(receiverId);

        // 私信要求双方互相关注（2026-09-11 冻结规则）。
        // 校验必须在写入 message / 创建会话之前，否则非互关场景会先落库再报错。
        // 历史消息读取（listMessages）与标记已读（markRead）不受此限制。
        if (!followStatusLoader.isMutual(userId, receiverId)) {
            throw BizException.of(ErrorCode.NOT_MUTUAL_FOLLOW);
        }

        String content = request.content().trim();
        if (content.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "消息内容不能为空");
        }

        // 会话按 (较小, 较大) 规整后复用或创建，客户端无需先建会话
        Conversation conversation = conversationService.getOrCreateEntity(userId, receiverId);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(userId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setIsRead(0);
        messageMapper.insert(message);

        // 最后消息冗余字段 + 接收方未读数在 SQL 里一次原子更新
        conversationMapper.updateOnNewMessage(
                conversation.getId(),
                message.getId(),
                truncate(content),
                message.getCreateTime(),
                receiverId);

        log.info("发送私信: messageId={}, conversationId={}, senderId={}, receiverId={}",
                message.getId(), conversation.getId(), userId, receiverId);

        return new MessageVO(message.getId(), conversation.getId(), userId, receiverId,
                content, true, false, message.getCreateTime());
    }

    @Override
    public List<MessageVO> listMessages(Long userId, Long conversationId, MessageQuery query) {
        conversationService.requireMember(conversationId, userId);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                // 用 id 而不是 create_time 做游标：主键唯一且单调，
                // 时间戳在批量插入时可能完全相同，会导致翻页时漏掉或重复
                .orderByDesc(Message::getId);

        if (query.getBeforeId() != null) {
            wrapper.lt(Message::getId, query.getBeforeId());
        }

        // 用 Page(1, size) 而不是 last("LIMIT n")：OFFSET 恒为 0 正好对应游标语义，
        // 且 limit 作为绑定参数传入，不拼接 SQL
        IPage<Message> page = messageMapper.selectPage(
                new Page<>(1, query.sizeOrDefault()), wrapper);

        List<Message> records = new ArrayList<>(page.getRecords());
        // 查出来是按 id 倒序，反转成时间正序交付
        Collections.reverse(records);

        return records.stream()
                .map(message -> new MessageVO(
                        message.getId(),
                        message.getConversationId(),
                        message.getSenderId(),
                        message.getReceiverId(),
                        message.getContent(),
                        userId.equals(message.getSenderId()),
                        message.getIsRead() != null && message.getIsRead() == 1,
                        message.getCreateTime()))
                .toList();
    }

    @Override
    public long unreadTotal(Long userId) {
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return conversationMapper.sumUnread(userId);
    }

    /** 会话列表里的预览文案，超长截断，避免把整段长消息塞进列表接口。 */
    private String truncate(String content) {
        int max = BizConstants.MESSAGE_PREVIEW_LENGTH;
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.length() <= max ? content : content.substring(0, max);
    }
}
