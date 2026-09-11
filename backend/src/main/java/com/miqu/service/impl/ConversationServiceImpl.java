package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.entity.Conversation;
import com.miqu.mapper.ConversationMapper;
import com.miqu.mapper.MessageMapper;
import com.miqu.service.ConversationService;
import com.miqu.service.UserService;
import com.miqu.service.support.FollowStatusLoader;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.ConversationVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserService userService;
    private final UserBriefLoader userBriefLoader;
    private final FollowStatusLoader followStatusLoader;

    @Override
    public PageResult<ConversationVO> list(Long userId, PageQuery query) {
        userService.requireActiveUser(userId);

        Page<Conversation> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Conversation> result = conversationMapper.selectPage(page,
                new LambdaQueryWrapper<Conversation>()
                        .and(w -> w.eq(Conversation::getUser1Id, userId)
                                .or()
                                .eq(Conversation::getUser2Id, userId))
                        // 过滤掉"建了但一条消息都没发过"的空会话，否则列表里会出现空白条目
                        .isNotNull(Conversation::getLastMessageTime)
                        .orderByDesc(Conversation::getLastMessageTime)
                        .orderByDesc(Conversation::getId));

        List<Conversation> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        // 批量加载对方信息，避免逐条查询
        Map<Long, UserBriefVO> partners = userBriefLoader.load(
                records.stream().map(c -> c.partnerOf(userId)).toList());

        List<ConversationVO> list = records.stream().map(conversation -> new ConversationVO(
                conversation.getId(),
                partners.getOrDefault(conversation.partnerOf(userId), UserBriefLoader.deletedPlaceholder()),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageTime(),
                unreadOf(conversation, userId)
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO openConversation(Long userId, Long targetUserId) {
        userService.requireActiveUser(userId);
        if (userId.equals(targetUserId)) {
            throw BizException.of(ErrorCode.CANNOT_MESSAGE_SELF);
        }
        userService.requireActiveUser(targetUserId);

        // 私信要求双方互相关注（2026-09-11 冻结规则）：非互关不能打开/创建会话。
        // 只限制"发起"，不影响历史会话的读取——listMessages / markRead 走 requireMember，不在此处。
        if (!followStatusLoader.isMutual(userId, targetUserId)) {
            throw BizException.of(ErrorCode.NOT_MUTUAL_FOLLOW);
        }

        Conversation conversation = getOrCreateEntity(userId, targetUserId);
        return new ConversationVO(
                conversation.getId(),
                userBriefLoader.loadOne(conversation.partnerOf(userId)),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageTime(),
                unreadOf(conversation, userId));
    }

    @Override
    public Conversation requireMember(Long conversationId, Long userId) {
        if (conversationId == null) {
            throw BizException.of(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw BizException.of(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conversation.isMember(userId)) {
            throw BizException.of(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long conversationId) {
        Conversation conversation = requireMember(conversationId, userId);

        // message.is_read 与 conversation.*_unread 是同一事实的两种表示，必须同事务更新，
        // 否则会出现"角标显示有未读，点进去却是空的"
        messageMapper.markConversationRead(conversationId, userId, LocalDateTime.now());

        Conversation update = new Conversation();
        update.setId(conversationId);
        if (conversation.getUser1Id().equals(userId)) {
            update.setUser1Unread(0);
        } else {
            update.setUser2Unread(0);
        }
        conversationMapper.updateById(update);

        log.debug("会话标记已读: conversationId={}, userId={}", conversationId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation getOrCreateEntity(Long userIdA, Long userIdB) {
        // 规整成 (较小, 较大)：会话表有 CHECK (user1_id < user2_id)，
        // 且 uk_users 唯一键只有在顺序一致时才能让 (A,B) 与 (B,A) 命中同一条记录
        long user1 = Math.min(userIdA, userIdB);
        long user2 = Math.max(userIdA, userIdB);

        Conversation existing = find(user1, user2);
        if (existing != null) {
            return existing;
        }

        Conversation created = new Conversation();
        created.setUser1Id(user1);
        created.setUser2Id(user2);
        created.setLastMessagePreview("");
        created.setUser1Unread(0);
        created.setUser2Unread(0);

        try {
            conversationMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时创建同一会话，由 uk_users 兜底。
            // MySQL 的唯一键冲突只回滚该条语句、不会中止事务，因此这里可以安全地重查一次。
            Conversation raced = find(user1, user2);
            if (raced == null) {
                throw e;
            }
            log.debug("会话并发创建，复用已存在的一条: user1={}, user2={}", user1, user2);
            return raced;
        }
    }

    private Conversation find(long user1, long user2) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUser1Id, user1)
                .eq(Conversation::getUser2Id, user2));
    }

    /** 取当前用户在该会话里的未读数。 */
    private int unreadOf(Conversation conversation, Long userId) {
        Integer value = conversation.getUser1Id().equals(userId)
                ? conversation.getUser1Unread()
                : conversation.getUser2Unread();
        return value == null ? 0 : value;
    }
}
