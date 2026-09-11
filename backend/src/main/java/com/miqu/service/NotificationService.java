package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.NotificationQuery;
import com.miqu.vo.NotificationUnreadVO;
import com.miqu.vo.NotificationVO;

/**
 * 通知服务（内部服务，一期不对外暴露接口）。
 *
 * <p>通知在关注 / 点赞 / 评论三个动作的事务内同步创建，**不产生私信类通知**——
 * 私信的新消息提示由会话未读数承载，避免同一件事在通知页与消息页重复出现。
 *
 * <p>统一的自我过滤规则：触发者与接收者是同一人时不产生通知。
 * 这条规则收在实现类里，调用方不需要各自判断。
 */
public interface NotificationService {

    /** 有人关注了我。 */
    void notifyFollow(Long actorId, Long targetUserId);

    /** 有人点赞了我的动态。 */
    void notifyLike(Long actorId, Long postAuthorId, Long postId);

    /** 有人评论了我的动态。 */
    void notifyComment(Long actorId, Long postAuthorId, Long postId, Long commentId, String commentContent);

    /**
     * 取消点赞时撤回对应的通知。
     *
     * <p>"取消点赞"与"动态被删"语义不同：前者表示这个动作从未发生，应当撤回通知；
     * 后者表示动作发生过但目标没了，通知保留（靠 content 快照渲染）。
     */
    void removeLikeNotification(Long actorId, Long targetUserId, Long postId);

    /** 取消关注时撤回对应的通知。 */
    void removeFollowNotification(Long actorId, Long targetUserId);

    /** 动态被删除时，清理挂在该动态上的点赞与评论通知，避免通知页出现点不开的条目。 */
    void removePostNotifications(Long postId);

    // ==================== 查询与已读（对外接口使用） ====================

    /** 通知列表，按时间倒序，可按类型与已读状态过滤。 */
    PageResult<NotificationVO> list(Long userId, NotificationQuery query);

    /** 未读数（按关注/点赞/评论拆开）。 */
    NotificationUnreadVO unreadCount(Long userId);

    /** 单条标记已读。不是本人的通知返回 403。 */
    void markRead(Long userId, Long notificationId);

    /** 全部标记已读。 */
    void markAllRead(Long userId);
}
