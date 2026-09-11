package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.entity.Conversation;
import com.miqu.vo.ConversationVO;

public interface ConversationService {

    /** 会话列表，按最后消息时间倒序。只返回已经有消息的会话。 */
    PageResult<ConversationVO> list(Long userId, PageQuery query);

    /** 打开（获取或创建）与某人的会话，返回会话信息。幂等。 */
    ConversationVO openConversation(Long userId, Long targetUserId);

    /**
     * 取出会话实体并校验当前用户是参与者。
     *
     * <p>非参与者返回 403 而不是 404：用户确实无权访问这个存在的会话，
     * 与"会话不存在"区分开，测试用例也能分别断言。
     */
    Conversation requireMember(Long conversationId, Long userId);

    /** 标记该会话中我收到的消息为已读，同时把权威未读数清零。 */
    void markRead(Long userId, Long conversationId);

    /**
     * 按 (较小 ID, 较大 ID) 规整后取回或创建会话。
     *
     * <p>供发消息复用。**调用方必须已经校验过两个用户都可用**。
     */
    Conversation getOrCreateEntity(Long userIdA, Long userIdB);
}
