package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.common.SqlLikeUtils;
import com.miqu.common.enums.RoleEnum;
import com.miqu.common.enums.UserStatusEnum;
import com.miqu.config.MiquProperties;
import com.miqu.converter.UserConverter;
import com.miqu.dto.query.UserSearchQuery;
import com.miqu.dto.request.ChangePasswordRequest;
import com.miqu.dto.request.UpdateAvatarRequest;
import com.miqu.dto.request.UpdateProfileRequest;
import com.miqu.entity.User;
import com.miqu.mapper.UserMapper;
import com.miqu.service.UserService;
import com.miqu.service.support.FollowStatusLoader;
import com.miqu.vo.UserProfileVO;
import com.miqu.vo.UserSearchVO;
import com.miqu.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FollowStatusLoader followStatusLoader;
    private final PasswordEncoder passwordEncoder;
    private final MiquProperties properties;

    @Override
    public UserVO getCurrentUser(Long userId) {
        return UserConverter.toVO(requireActiveUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        requireActiveUser(userId);

        User update = new User();
        update.setId(userId);
        update.setNickname(request.nickname().trim());
        // gender / birthday / bio 为 null 表示"不修改"，MP 的 updateById 会跳过 null 字段；
        // 若要清空简介，前端应显式传空字符串而不是 null。
        update.setGender(request.gender());
        update.setBirthday(request.birthday());
        update.setBio(request.bio());
        userMapper.updateById(update);

        log.info("用户修改资料: userId={}", userId);
        return getCurrentUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = requireActiveUser(userId);

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(update);

        // 只记录"发生了修改"这一事实，不记录任何新旧密码
        log.info("用户修改密码: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateAvatar(Long userId, UpdateAvatarRequest request) {
        requireActiveUser(userId);

        String avatar = request.avatar().trim();

        // 与发布动态的图片校验保持一致：只接受本项目上传接口返回的地址。
        // 否则用户可以把自己的头像指向任意外链（追踪像素、违规图），
        // 服务器既成了别人的图床，内容审核也无从下手。
        // 前缀必须带 "/"，否则 "/uploads-evil/x.png" 这类近似串会蒙混过关。
        String prefix = properties.getUpload().getUrlPrefix() + "/";
        if (!avatar.startsWith(prefix)) {
            throw BizException.of(ErrorCode.INVALID_IMAGE_URL);
        }

        User update = new User();
        update.setId(userId);
        update.setAvatar(avatar);
        userMapper.updateById(update);

        log.info("用户修改头像: userId={}", userId);
        return getCurrentUser(userId);
    }

    @Override
    public User requireActiveUser(Long userId) {
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        // 逻辑删除由 MyBatis-Plus 自动过滤，查不到即视为不存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        if (!UserStatusEnum.isNormal(user.getStatus())) {
            throw BizException.of(ErrorCode.USER_DISABLED);
        }
        return user;
    }

    @Override
    public User requireVisibleUser(Long userId) {
        if (userId == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        // 逻辑删除由 MyBatis-Plus 自动过滤，查不到即视为不存在；禁用的用户仍可被看到
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public UserProfileVO getProfile(Long currentUserId, Long targetUserId) {
        User target = requireVisibleUser(targetUserId);

        // 经 FollowStatusLoader 而不是 FollowService：后者依赖 UserService，
        // 反向注入会形成循环依赖
        boolean followedByMe = false;
        boolean followingMe = false;
        if (currentUserId != null && !currentUserId.equals(targetUserId)) {
            followedByMe = followStatusLoader.isFollowing(currentUserId, targetUserId);
            followingMe = followStatusLoader.isFollowing(targetUserId, currentUserId);
        }

        return new UserProfileVO(
                target.getId(),
                target.getUsername(),
                target.getNickname(),
                target.getGender(),
                target.getBio(),
                target.getAvatar(),
                target.getFollowingCount(),
                target.getFollowerCount(),
                target.getPostCount(),
                followedByMe,
                followingMe,
                followedByMe && followingMe,
                target.getCreateTime());
    }

    @Override
    public PageResult<UserSearchVO> search(Long currentUserId, UserSearchQuery query) {
        String keyword = query.getKeyword().trim();
        if (keyword.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "搜索关键词不能为空");
        }

        // 必须转义 LIKE 通配符：否则用户搜一个 "%" 就会命中全部用户，
        // 搜 "_" 会命中任意单字符。这是最容易被忽略、也最容易被测试挖出来的边界。
        String pattern = SqlLikeUtils.contains(keyword);

        Page<User> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<User> result = userMapper.selectPage(page, new LambdaQueryWrapper<User>()
                .and(w -> w.apply("nickname LIKE {0} ESCAPE '\\\\'", pattern)
                        .or()
                        .apply("username LIKE {0} ESCAPE '\\\\'", pattern))
                // 粉丝多的排前面：搜索结果里"谁都认识的人"更应该被看到
                .orderByDesc(User::getFollowerCount)
                .orderByDesc(User::getId));

        List<User> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        List<Long> userIds = records.stream().map(User::getId).toList();
        Set<Long> followedByMe = followStatusLoader.filterFollowed(currentUserId, userIds);

        List<UserSearchVO> list = records.stream().map(user -> new UserSearchVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getBio(),
                user.getFollowerCount(),
                followedByMe.contains(user.getId())
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long adminId, Long targetUserId, Integer status) {
        if (Objects.equals(adminId, targetUserId)) {
            throw BizException.of(ErrorCode.CANNOT_OPERATE_SELF);
        }

        User target = requireVisibleUser(targetUserId);
        // 禁用管理员账号会让对方连后台都进不去，属于误操作重灾区，直接拦掉
        if (RoleEnum.isAdmin(target.getRole())) {
            throw BizException.of(ErrorCode.CANNOT_DISABLE_ADMIN);
        }

        User update = new User();
        update.setId(targetUserId);
        update.setStatus(status);
        userMapper.updateById(update);

        log.info("修改用户状态: targetUserId={}, status={}, operatorId={}", targetUserId, status, adminId);
    }
}
