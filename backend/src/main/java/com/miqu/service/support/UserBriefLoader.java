package com.miqu.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miqu.entity.User;
import com.miqu.mapper.UserMapper;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量加载用户简要信息。
 *
 * <p>存在的唯一理由是**避免 N+1**：一页 10 条动态如果逐条查作者，
 * 就是 10 次额外查询。这里统一"收集 ID → 一次查回 → Map 回填"，
 * 让所有需要作者信息的模块（动态、评论、点赞列表、关注列表）共用同一套逻辑。
 */
@Component
@RequiredArgsConstructor
public class UserBriefLoader {

    private final UserMapper userMapper;

    /** 批量加载，返回 userId → UserBriefVO。入参为空时返回空 Map，不查库。 */
    public Map<Long, UserBriefVO> load(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinctIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().in(User::getId, distinctIds));
        Map<Long, UserBriefVO> result = new HashMap<>(users.size());
        for (User user : users) {
            result.put(user.getId(), toBrief(user));
        }
        return result;
    }

    /**
     * 取单个用户，缺失时返回一个占位对象。
     *
     * <p>用户被注销后，其历史动态仍可能存在。此时作者信息缺失，
     * 返回占位对象可以保证前端不会因为 author 为 null 而渲染崩溃。
     */
    public UserBriefVO loadOne(Long userId) {
        if (userId == null) {
            return deletedPlaceholder();
        }
        User user = userMapper.selectById(userId);
        return user == null ? deletedPlaceholder() : toBrief(user);
    }

    public static UserBriefVO toBrief(User user) {
        return new UserBriefVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatar(), user.getBio());
    }

    /** 作者已注销时的占位信息，保证前端不会因为 author 为 null 而渲染崩溃。 */
    public static UserBriefVO deletedPlaceholder() {
        return new UserBriefVO(null, null, "已注销用户", "", "");
    }
}
