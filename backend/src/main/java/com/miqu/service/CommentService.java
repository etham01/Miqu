package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.request.CommentCreateRequest;
import com.miqu.vo.CommentVO;

public interface CommentService {

    /** 发表评论。 */
    CommentVO create(Long userId, Long postId, CommentCreateRequest request);

    /** 动态的评论列表，按时间正序（先发的在前，读起来像对话）。 */
    PageResult<CommentVO> list(Long currentUserId, Long postId, PageQuery query);

    /** 删除评论。作者本人或管理员可删，其他情况 403。 */
    void delete(Long currentUserId, Long commentId);
}
