package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.entity.Follow;
import com.miqu.entity.User;
import com.miqu.mapper.FollowMapper;
import com.miqu.mapper.UserMapper;
import com.miqu.service.FollowService;
import com.miqu.service.NotificationService;
import com.miqu.service.UserService;
import com.miqu.service.support.FollowStatusLoader;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.FollowResultVO;
import com.miqu.vo.UserBriefVO;
import com.miqu.vo.UserFollowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final UserBriefLoader userBriefLoader;
    private final FollowStatusLoader followStatusLoader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResultVO follow(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        if (currentUserId.equals(targetUserId)) {
            throw BizException.of(ErrorCode.CANNOT_FOLLOW_SELF);
        }

        userService.requireActiveUser(currentUserId);
        // 目标被禁用时返回 423，而不是让它悄悄积累粉丝
        userService.requireActiveUser(targetUserId);

        // 快速失败，给出干净的业务提示。
        // 注意这只是体验优化：并发下真正拦住重复的是 uk_follower_following 唯一键，
        // 冲突时抛 DuplicateKeyException，由 GlobalExceptionHandler 翻译成同一个 409。
        if (followStatusLoader.isFollowing(currentUserId, targetUserId)) {
            throw BizException.of(ErrorCode.ALREADY_FOLLOWED);
        }

        Follow follow = new Follow();
        follow.setFollowerId(currentUserId);
        follow.setFollowingId(targetUserId);
        followMapper.insert(follow);

        userMapper.incrFollowingCount(currentUserId);
        userMapper.incrFollowerCount(targetUserId);
        notificationService.notifyFollow(currentUserId, targetUserId);

        log.info("关注成功: followerId={}, followingId={}", currentUserId, targetUserId);
        return new FollowResultVO(true, freshFollowerCount(targetUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResultVO unfollow(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        userService.requireVisibleUser(targetUserId);

        // 对称关系表必须物理删除，删除行数为 0 说明本来就没关注
        int deleted = followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, currentUserId)
                .eq(Follow::getFollowingId, targetUserId));
        if (deleted == 0) {
            throw BizException.of(ErrorCode.NOT_FOLLOWED);
        }

        userMapper.decrFollowingCount(currentUserId);
        userMapper.decrFollowerCount(targetUserId);
        notificationService.removeFollowNotification(currentUserId, targetUserId);

        log.info("取消关注: followerId={}, followingId={}", currentUserId, targetUserId);
        return new FollowResultVO(false, freshFollowerCount(targetUserId));
    }

    @Override
    public PageResult<UserFollowVO> listFollowing(Long currentUserId, Long targetUserId, PageQuery query) {
        userService.requireVisibleUser(targetUserId);
        return pageRelations(currentUserId, targetUserId, query, Follow::getFollowingId, true);
    }

    @Override
    public PageResult<UserFollowVO> listFollowers(Long currentUserId, Long targetUserId, PageQuery query) {
        userService.requireVisibleUser(targetUserId);
        return pageRelations(currentUserId, targetUserId, query, Follow::getFollowerId, false);
    }

    /**
     * 关注/粉丝列表的公共分页逻辑。
     *
     * <p>"分页查关系 → 批量查用户 → 批量查关注状态 → Map 回填"四步，
     * 无论一页多少条都只发 3 次查询。
     */
    private PageResult<UserFollowVO> pageRelations(Long currentUserId, Long targetUserId, PageQuery query,
                                                   Function<Follow, Long> idExtractor, boolean followingList) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                .eq(followingList ? Follow::getFollowerId : Follow::getFollowingId, targetUserId)
                .orderByDesc(Follow::getCreateTime)
                .orderByDesc(Follow::getId);

        Page<Follow> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Follow> result = followMapper.selectPage(page, wrapper);

        List<Follow> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        List<Long> userIds = records.stream().map(idExtractor).toList();
        Map<Long, UserBriefVO> users = userBriefLoader.load(userIds);
        Set<Long> followedByMe = followStatusLoader.filterFollowed(currentUserId, userIds);

        List<UserFollowVO> list = records.stream().map(relation -> {
            Long userId = idExtractor.apply(relation);
            UserBriefVO user = users.getOrDefault(userId, UserBriefLoader.deletedPlaceholder());
            return new UserFollowVO(user.id(), user.username(), user.nickname(), user.avatar(), user.bio(),
                    followedByMe.contains(userId), relation.getCreateTime());
        }).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    /**
     * 重新读取粉丝数。
     *
     * <p>不能直接用进入方法时查到的值 +1：那个值可能已经被并发操作改过。
     * 上面刚执行过 UPDATE，MyBatis 会清空一级缓存，因此这次 SELECT 一定是数据库的最新值。
     */
    private int freshFollowerCount(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || user.getFollowerCount() == null ? 0 : user.getFollowerCount();
    }
}
