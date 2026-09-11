package com.miqu.converter;

import com.miqu.entity.User;
import com.miqu.vo.UserVO;

/**
 * 用户实体与 VO 之间的转换。
 *
 * <p>集中在一处，避免各 Service 里散落大段手写 setter ——那些地方最容易
 * 一个不注意就把 password 带进响应。
 */
public final class UserConverter {

    private UserConverter() {
    }

    /** 转成当前用户可见的完整信息（含邮箱，不含密码）。 */
    public static UserVO toVO(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getGender(),
                user.getBirthday(),
                user.getBio(),
                user.getAvatar(),
                user.getRole(),
                user.getFollowingCount(),
                user.getFollowerCount(),
                user.getPostCount(),
                user.getCreateTime());
    }
}
