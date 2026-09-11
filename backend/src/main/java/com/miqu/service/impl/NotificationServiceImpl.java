package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizConstants;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.common.enums.NotificationTypeEnum;
import com.miqu.dto.query.NotificationQuery;
import com.miqu.entity.Notification;
import com.miqu.mapper.NotificationMapper;
import com.miqu.service.NotificationService;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.NotificationUnreadVO;
import com.miqu.vo.NotificationVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserBriefLoader userBriefLoader;

    @Override
    public void notifyFollow(Long actorId, Long targetUserId) {
        create(targetUserId, NotificationTypeEnum.FOLLOW, actorId, null, null, null);
    }

    @Override
    public void notifyLike(Long actorId, Long postAuthorId, Long postId) {
        create(postAuthorId, NotificationTypeEnum.LIKE, actorId, postId, null, null);
    }

    @Override
    public void notifyComment(Long actorId, Long postAuthorId, Long postId,
                              Long commentId, String commentContent) {
        create(postAuthorId, NotificationTypeEnum.COMMENT, actorId, postId, commentId, commentContent);
    }

    @Override
    public void removeLikeNotification(Long actorId, Long targetUserId, Long postId) {
        if (targetUserId == null || targetUserId.equals(actorId)) {
            return;
        }
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, targetUserId)
                .eq(Notification::getActorId, actorId)
                .eq(Notification::getPostId, postId)
                .eq(Notification::getType, NotificationTypeEnum.LIKE.getCode()));
    }

    @Override
    public void removeFollowNotification(Long actorId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(actorId)) {
            return;
        }
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, targetUserId)
                .eq(Notification::getActorId, actorId)
                .eq(Notification::getType, NotificationTypeEnum.FOLLOW.getCode()));
    }

    @Override
    public void removePostNotifications(Long postId) {
        if (postId == null) {
            return;
        }
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getPostId, postId)
                .in(Notification::getType,
                        List.of(NotificationTypeEnum.LIKE.getCode(), NotificationTypeEnum.COMMENT.getCode())));
    }

    // ==================== 查询与已读 ====================

    @Override
    public PageResult<NotificationVO> list(Long userId, NotificationQuery query) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);
        if (query.getType() != null) {
            wrapper.eq(Notification::getType, query.getType());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, query.getIsRead());
        }

        Page<Notification> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Notification> result = notificationMapper.selectPage(page, wrapper);

        List<Notification> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        Map<Long, UserBriefVO> actors = userBriefLoader.load(
                records.stream().map(Notification::getActorId).toList());

        List<NotificationVO> list = records.stream().map(notification -> new NotificationVO(
                notification.getId(),
                notification.getType(),
                actors.getOrDefault(notification.getActorId(), UserBriefLoader.deletedPlaceholder()),
                notification.getPostId(),
                notification.getCommentId(),
                notification.getContent(),
                notification.getIsRead() != null && notification.getIsRead() == 1,
                notification.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    /**
     * 未读数。
     *
     * <p>刻意用三次 {@code COUNT} 而不是一次分组聚合：查询走
     * {@code idx_user_read_time(user_id, is_read, create_time)} 索引，
     * 本项目规模下三次往返的代价可以忽略，而代码可读性明显更好。
     * 若将来通知量级变大，再改成一条 {@code GROUP BY type} 的聚合查询。
     */
    @Override
    public NotificationUnreadVO unreadCount(Long userId) {
        long follow = countUnread(userId, NotificationTypeEnum.FOLLOW);
        long like = countUnread(userId, NotificationTypeEnum.LIKE);
        long comment = countUnread(userId, NotificationTypeEnum.COMMENT);
        return new NotificationUnreadVO(follow + like + comment, follow, like, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw BizException.of(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        // 别人的通知返回 403 而不是 404：通知本身存在，只是无权操作。
        // 两者区分开，测试才能分别断言
        if (!notification.getUserId().equals(userId)) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }
        // 幂等：已读的再标记一次直接返回，不产生多余的 UPDATE
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return;
        }

        Notification update = new Notification();
        update.setId(notificationId);
        update.setIsRead(1);
        notificationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        log.debug("通知全部标记已读: userId={}", userId);
    }

    private long countUnread(Long userId, NotificationTypeEnum type) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .eq(Notification::getType, type.getCode()));
    }

    /**
     * 创建通知。
     *
     * <p>{@code receiverId.equals(actorId)} 时直接返回——自己关注/点赞/评论自己不应该收到通知。
     * 这条规则放在这里而不是各调用方，是为了避免某个新模块忘记判断。
     */
    private void create(Long receiverId, NotificationTypeEnum type, Long actorId,
                        Long postId, Long commentId, String snapshot) {
        if (receiverId == null || actorId == null || receiverId.equals(actorId)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(receiverId);
        notification.setType(type.getCode());
        notification.setActorId(actorId);
        notification.setPostId(postId);
        notification.setCommentId(commentId);
        // 快照：关联数据被删后通知仍可读，不会出现空白条目
        notification.setContent(truncate(snapshot));
        notification.setIsRead(0);

        notificationMapper.insert(notification);
        log.debug("生成通知: type={}, receiverId={}, actorId={}, postId={}",
                type.getCode(), receiverId, actorId, postId);
    }

    private String truncate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        int max = BizConstants.NOTIFICATION_SNAPSHOT_LENGTH;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
