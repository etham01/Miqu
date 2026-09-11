package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.vo.FollowResultVO;
import com.miqu.vo.UserFollowVO;

public interface FollowService {

    /** 关注。自己关注自己 400，目标不存在 404，目标被禁用 423，重复关注 409。 */
    FollowResultVO follow(Long currentUserId, Long targetUserId);

    /** 取消关注。本来就没关注时返回 404（语义明确、可断言）。 */
    FollowResultVO unfollow(Long currentUserId, Long targetUserId);

    /** 某人关注的人。 */
    PageResult<UserFollowVO> listFollowing(Long currentUserId, Long targetUserId, PageQuery query);

    /** 某人的粉丝。 */
    PageResult<UserFollowVO> listFollowers(Long currentUserId, Long targetUserId, PageQuery query);
}
