package com.miqu.service;

import com.miqu.dto.request.ChangePasswordRequest;
import com.miqu.dto.request.UpdateAvatarRequest;
import com.miqu.dto.request.UpdateProfileRequest;
import com.miqu.common.PageResult;
import com.miqu.dto.query.UserSearchQuery;
import com.miqu.entity.User;
import com.miqu.vo.UserProfileVO;
import com.miqu.vo.UserSearchVO;
import com.miqu.vo.UserVO;

public interface UserService {

    /** 获取当前登录用户信息。 */
    UserVO getCurrentUser(Long userId);

    /** 修改个人资料。 */
    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    /** 修改密码，需校验原密码。 */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** 修改头像。 */
    UserVO updateAvatar(Long userId, UpdateAvatarRequest request);

    /**
     * 获取一个"可用"的用户（存在、未删除、未被禁用），否则抛出对应业务异常。
     *
     * <p>供其他模块复用：关注、点赞、发私信等操作都需要先确认目标用户是否可用，
     * 把这段判断收在一处，避免每个 Service 各写一遍且校验口径不一致。
     */
    User requireActiveUser(Long userId);

    /**
     * 取出一个"可见"的用户：只要求存在（已注销视为不存在），**不要求状态正常**。
     *
     * <p>与 {@link #requireActiveUser} 的区别：被禁用的用户其主页仍然可以浏览，
     * 只是不能与之互动（关注、发私信等仍会返回 423）。因此查看主页用本方法，
     * 涉及写操作时用 {@link #requireActiveUser}。
     */
    User requireVisibleUser(Long userId);

    /**
     * 用户公开主页信息。
     *
     * @param currentUserId 当前登录用户；游客传 null，此时关注状态一律为 false
     */
    UserProfileVO getProfile(Long currentUserId, Long targetUserId);

    /**
     * 按昵称 / 用户名搜索用户。
     *
     * <p>使用 MySQL LIKE，不引入 Elasticsearch——本项目规模下没必要，
     * 但关键词中的 {@code %} 与 {@code _} 会被转义，不会退化成"匹配全部"。
     *
     * @param currentUserId 当前登录用户；游客传 null，此时 followedByMe 恒为 false
     */
    PageResult<UserSearchVO> search(Long currentUserId, UserSearchQuery query);

    /**
     * 管理后台：修改用户状态（1 正常 / 0 禁用）。
     *
     * <p>不能操作自己，也不能禁用管理员账号。
     * 禁用会立即生效——认证过滤器每请求查库校验状态，被禁用者的旧 Token 随即失效。
     */
    void updateStatus(Long adminId, Long targetUserId, Integer status);
}
