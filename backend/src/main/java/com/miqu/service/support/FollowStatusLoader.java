package com.miqu.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miqu.entity.Follow;
import com.miqu.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注状态查询。
 *
 * <p>独立出来的原因：{@code UserService} 需要判断关注关系（渲染主页的"已关注/互相关注"），
 * 而 {@code FollowService} 又依赖 {@code UserService} 做用户校验——
 * 两边互相注入会形成循环依赖。把这段查询抽到中立的位置，双方都依赖它即可。
 */
@Component
@RequiredArgsConstructor
public class FollowStatusLoader {

    private final FollowMapper followMapper;

    /** followerId 是否关注了 followingId。任一参数为 null 时返回 false。 */
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) {
            return false;
        }
        return followMapper.exists(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFollowingId, followingId));
    }

    /**
     * 从候选 ID 中筛出 followerId 已关注的那些。
     *
     * <p>列表场景必须用批量版本：逐条调用 {@link #isFollowing} 会退化成 N+1。
     */
    public Set<Long> filterFollowed(Long followerId, Collection<Long> candidateIds) {
        if (followerId == null || candidateIds == null || candidateIds.isEmpty()) {
            return Set.of();
        }
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, followerId)
                        .in(Follow::getFollowingId, candidateIds))
                .stream()
                .map(Follow::getFollowingId)
                .collect(Collectors.toSet());
    }
}
