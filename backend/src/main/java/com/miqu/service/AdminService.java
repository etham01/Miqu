package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.AdminCommentQuery;
import com.miqu.dto.query.AdminLogQuery;
import com.miqu.dto.query.AdminPostQuery;
import com.miqu.dto.query.AdminUserQuery;
import com.miqu.vo.AdminCommentVO;
import com.miqu.vo.AdminOperationLogVO;
import com.miqu.vo.AdminPostVO;
import com.miqu.vo.AdminStatsVO;
import com.miqu.vo.AdminUserVO;

/**
 * 管理后台的查询与处置。
 *
 * <p>删除动态 / 评论复用普通模块的 Service：
 * 它们的权限分支已经是"作者或管理员"，这里不需要另写一套。
 * 本类只负责补上管理员操作日志。
 */
public interface AdminService {

    /** 首页统计。 */
    AdminStatsVO stats();

    PageResult<AdminUserVO> listUsers(AdminUserQuery query);

    AdminUserVO getUserDetail(Long userId);

    /** 修改用户状态并记日志。 */
    void updateUserStatus(Long adminId, Long targetUserId, Integer status);

    PageResult<AdminPostVO> listPosts(AdminPostQuery query);

    /** 管理员删除动态并记日志。 */
    void deletePost(Long adminId, Long postId);

    PageResult<AdminCommentVO> listComments(AdminCommentQuery query);

    /** 管理员删除评论并记日志。 */
    void deleteComment(Long adminId, Long commentId);

    PageResult<AdminOperationLogVO> listLogs(AdminLogQuery query);
}
