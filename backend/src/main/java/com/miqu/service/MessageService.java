package com.miqu.service;

import com.miqu.dto.query.MessageQuery;
import com.miqu.dto.request.MessageSendRequest;
import com.miqu.vo.MessageVO;

import java.util.List;

public interface MessageService {

    /** 发送私信。会话不存在时自动创建。 */
    MessageVO send(Long userId, MessageSendRequest request);

    /**
     * 聊天记录（游标分页）。
     *
     * <p>返回结果按时间**正序**排列，便于前端直接从上往下渲染。
     * 传入上一页最早一条的 id 作为 {@code beforeId} 即可继续往前翻。
     */
    List<MessageVO> listMessages(Long userId, Long conversationId, MessageQuery query);

    /** 私信未读总数（跨所有会话）。 */
    long unreadTotal(Long userId);
}
