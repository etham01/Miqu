package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.Conversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 会话 Mapper。
 *
 * <p>表不变式：{@code user1_id < user2_id}（DB 层有 CHECK 约束兜底），
 * 配合 {@code uk_users} 唯一键，使 (A,B) 与 (B,A) 天然是同一条记录。
 * 因此按"两个用户查会话"时**必须**先做 min/max 规整，否则查不到。
 */
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 新消息到达时更新会话冗余字段与接收方未读数。
     *
     * <p>用 {@code IF(user1_id = receiverId, 1, 0)} 在 SQL 里挑选该给哪一列 +1，
     * 而不是在 Java 里先判断再拼两条不同的 UPDATE：
     * 这样只需一次往返，且增减量在数据库侧原子完成，不会与并发请求互相覆盖。
     */
    @Update("""
            UPDATE `conversation`
            SET last_message_id = #{messageId},
                last_message_preview = #{preview},
                last_message_time = #{messageTime},
                user1_unread = user1_unread + IF(user1_id = #{receiverId}, 1, 0),
                user2_unread = user2_unread + IF(user2_id = #{receiverId}, 1, 0)
            WHERE id = #{conversationId}
            """)
    int updateOnNewMessage(@Param("conversationId") Long conversationId,
                           @Param("messageId") Long messageId,
                           @Param("preview") String preview,
                           @Param("messageTime") LocalDateTime messageTime,
                           @Param("receiverId") Long receiverId);

    /**
     * 某个用户的私信未读总数。
     *
     * <p>直接在会话表上求和，不去扫 message 表：
     * {@code conversation.user*_unread} 是未读数的权威值，message 表可能随消息量增长。
     * 用 {@code IF} 在 SQL 里挑选该读哪一列，避免两次查询。
     */
    @Select("""
            SELECT COALESCE(SUM(IF(user1_id = #{userId}, user1_unread, user2_unread)), 0)
            FROM `conversation`
            WHERE user1_id = #{userId} OR user2_id = #{userId}
            """)
    long sumUnread(@Param("userId") Long userId);
}
