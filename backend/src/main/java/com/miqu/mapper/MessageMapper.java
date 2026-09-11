package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.Message;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 私信 Mapper。
 *
 * <p>注意 {@code is_read} 与 {@code conversation.*_unread} 是同一事实的两种表示，
 * 任何修改未读状态的操作都必须**同事务**更新两者，否则会出现"角标显示有未读，
 * 点进去却没有"这类不一致。
 */
public interface MessageMapper extends BaseMapper<Message> {

    /** 把某个用户在指定会话里收到的未读消息全部置为已读。 */
    @Update("""
            UPDATE `message`
            SET is_read = 1, read_time = #{readTime}
            WHERE conversation_id = #{conversationId}
              AND receiver_id = #{userId}
              AND is_read = 0
              AND deleted = 0
            """)
    int markConversationRead(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId,
                             @Param("readTime") LocalDateTime readTime);
}
