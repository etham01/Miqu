package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.query.PostQuery;
import com.miqu.dto.request.PostCreateRequest;
import com.miqu.entity.Post;
import com.miqu.vo.LikeResultVO;
import com.miqu.vo.PostVO;
import com.miqu.vo.UserBriefVO;

public interface PostService {

    /** 发布动态。内容与图片不能同时为空，图片最多 9 张。 */
    PostVO create(Long userId, PostCreateRequest request);

    /** 首页动态流。tab=latest 最新，tab=following 我关注的人（需登录）。 */
    PageResult<PostVO> listFeed(Long currentUserId, PostQuery query);

    /** 某个用户发布的动态。 */
    PageResult<PostVO> listByUser(Long currentUserId, Long targetUserId, PageQuery query);

    /** 动态详情。 */
    PostVO getDetail(Long currentUserId, Long postId);

    /** 删除动态。作者本人或管理员可删，其他情况 403。 */
    void delete(Long currentUserId, Long postId);

    /** 点赞。重复点赞 409。 */
    LikeResultVO like(Long userId, Long postId);

    /** 取消点赞。本来就没点赞时 404。 */
    LikeResultVO unlike(Long userId, Long postId);

    /** 某条动态的点赞用户列表。 */
    PageResult<UserBriefVO> listLikes(Long postId, PageQuery query);

    /**
     * 取出未删除的动态，不存在则抛 404。
     *
     * <p>对其它模块（评论）开放，避免它们各自写一遍存在性判断导致校验口径不一致。
     */
    Post requirePost(Long postId);
}
