package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.common.SqlLikeUtils;
import com.miqu.common.enums.ReportStatusEnum;
import com.miqu.common.enums.TargetTypeEnum;
import com.miqu.common.enums.UserStatusEnum;
import com.miqu.dto.query.AdminCommentQuery;
import com.miqu.dto.query.AdminLogQuery;
import com.miqu.dto.query.AdminPostQuery;
import com.miqu.dto.query.AdminUserQuery;
import com.miqu.entity.AdminOperationLog;
import com.miqu.entity.Comment;
import com.miqu.entity.Post;
import com.miqu.entity.PostImage;
import com.miqu.entity.Report;
import com.miqu.entity.User;
import com.miqu.mapper.AdminOperationLogMapper;
import com.miqu.mapper.CommentMapper;
import com.miqu.mapper.PostImageMapper;
import com.miqu.mapper.PostMapper;
import com.miqu.mapper.ReportMapper;
import com.miqu.mapper.UserMapper;
import com.miqu.service.AdminLogService;
import com.miqu.service.AdminService;
import com.miqu.service.CommentService;
import com.miqu.service.PostService;
import com.miqu.service.UserService;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.AdminCommentVO;
import com.miqu.vo.AdminOperationLogVO;
import com.miqu.vo.AdminPostVO;
import com.miqu.vo.AdminStatsVO;
import com.miqu.vo.AdminUserVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final CommentMapper commentMapper;
    private final ReportMapper reportMapper;
    private final AdminOperationLogMapper adminOperationLogMapper;
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final AdminLogService adminLogService;
    private final UserBriefLoader userBriefLoader;

    // ==================== 统计 ====================

    @Override
    public AdminStatsVO stats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 逻辑删除条件由 MyBatis-Plus 自动追加，无需手写 deleted = 0
        long userTotal = userMapper.selectCount(null);
        long postTotal = postMapper.selectCount(null);
        long commentTotal = commentMapper.selectCount(null);

        long todayNewUser = userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, todayStart));
        long todayNewPost = postMapper.selectCount(
                new LambdaQueryWrapper<Post>().ge(Post::getCreateTime, todayStart));
        long todayNewComment = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().ge(Comment::getCreateTime, todayStart));

        long pendingReportTotal = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, ReportStatusEnum.PENDING.getCode()));

        return new AdminStatsVO(userTotal, postTotal, commentTotal,
                todayNewUser, todayNewPost, todayNewComment, pendingReportTotal);
    }

    // ==================== 用户管理 ====================

    @Override
    public PageResult<AdminUserVO> listUsers(AdminUserQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime)
                .orderByDesc(User::getId);

        if (query.hasKeyword()) {
            String pattern = SqlLikeUtils.contains(query.getKeyword());
            wrapper.and(w -> w.apply("username LIKE {0} ESCAPE '\\\\'", pattern)
                    .or()
                    .apply("nickname LIKE {0} ESCAPE '\\\\'", pattern));
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }

        Page<User> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<User> result = userMapper.selectPage(page, wrapper);

        List<AdminUserVO> list = result.getRecords().stream().map(this::toAdminUserVO).toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    public AdminUserVO getUserDetail(Long userId) {
        return toAdminUserVO(userService.requireVisibleUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long adminId, Long targetUserId, Integer status) {
        userService.updateStatus(adminId, targetUserId, status);

        boolean disabling = Objects.equals(status, UserStatusEnum.DISABLED.getCode());
        adminLogService.record(adminId,
                disabling ? AdminLogService.OP_DISABLE_USER : AdminLogService.OP_ENABLE_USER,
                TargetTypeEnum.USER.getCode(),
                targetUserId,
                (disabling ? "禁用用户 #" : "解禁用户 #") + targetUserId);
    }

    // ==================== 动态管理 ====================

    @Override
    public PageResult<AdminPostVO> listPosts(AdminPostQuery query) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .orderByDesc(Post::getCreateTime)
                .orderByDesc(Post::getId);

        if (query.getUserId() != null) {
            wrapper.eq(Post::getUserId, query.getUserId());
        }
        if (query.hasKeyword()) {
            wrapper.apply("content LIKE {0} ESCAPE '\\\\'", SqlLikeUtils.contains(query.getKeyword()));
        }

        Page<Post> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Post> result = postMapper.selectPage(page, wrapper);

        List<Post> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        List<Long> postIds = records.stream().map(Post::getId).toList();

        Map<Long, UserBriefVO> authors = userBriefLoader.load(
                records.stream().map(Post::getUserId).toList());

        Map<Long, List<String>> imageMap = postImageMapper.selectList(
                        new LambdaQueryWrapper<PostImage>()
                                .in(PostImage::getPostId, postIds)
                                .orderByAsc(PostImage::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(PostImage::getPostId,
                        Collectors.mapping(PostImage::getUrl, Collectors.toList())));

        List<AdminPostVO> list = records.stream().map(post -> new AdminPostVO(
                post.getId(),
                post.getContent(),
                imageMap.getOrDefault(post.getId(), List.of()),
                authors.getOrDefault(post.getUserId(), UserBriefLoader.deletedPlaceholder()),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long adminId, Long postId) {
        // 复用普通模块的删除逻辑：它已经会级联清理图片/点赞/评论并回滚计数，
        // 权限分支也已经是"作者或管理员"
        postService.delete(adminId, postId);
        adminLogService.record(adminId, AdminLogService.OP_DELETE_POST,
                TargetTypeEnum.POST.getCode(), postId, "删除动态 #" + postId);
    }

    // ==================== 评论管理 ====================

    @Override
    public PageResult<AdminCommentVO> listComments(AdminCommentQuery query) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .orderByDesc(Comment::getCreateTime)
                .orderByDesc(Comment::getId);

        if (query.getPostId() != null) {
            wrapper.eq(Comment::getPostId, query.getPostId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(Comment::getUserId, query.getUserId());
        }
        if (query.hasKeyword()) {
            wrapper.apply("content LIKE {0} ESCAPE '\\\\'", SqlLikeUtils.contains(query.getKeyword()));
        }

        Page<Comment> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Comment> result = commentMapper.selectPage(page, wrapper);

        List<Comment> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        Map<Long, UserBriefVO> authors = userBriefLoader.load(
                records.stream().map(Comment::getUserId).toList());

        List<AdminCommentVO> list = records.stream().map(comment -> new AdminCommentVO(
                comment.getId(),
                comment.getPostId(),
                comment.getContent(),
                authors.getOrDefault(comment.getUserId(), UserBriefLoader.deletedPlaceholder()),
                comment.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long adminId, Long commentId) {
        commentService.delete(adminId, commentId);
        adminLogService.record(adminId, AdminLogService.OP_DELETE_COMMENT,
                TargetTypeEnum.COMMENT.getCode(), commentId, "删除评论 #" + commentId);
    }

    // ==================== 操作日志 ====================

    @Override
    public PageResult<AdminOperationLogVO> listLogs(AdminLogQuery query) {
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<AdminOperationLog>()
                .orderByDesc(AdminOperationLog::getCreateTime)
                .orderByDesc(AdminOperationLog::getId);

        if (query.getAdminId() != null) {
            wrapper.eq(AdminOperationLog::getAdminId, query.getAdminId());
        }
        if (query.getOperationType() != null && !query.getOperationType().isBlank()) {
            wrapper.eq(AdminOperationLog::getOperationType, query.getOperationType().trim());
        }

        Page<AdminOperationLog> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<AdminOperationLog> result = adminOperationLogMapper.selectPage(page, wrapper);

        List<AdminOperationLog> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        Set<Long> adminIds = new HashSet<>();
        records.forEach(entry -> adminIds.add(entry.getAdminId()));
        Map<Long, UserBriefVO> admins = userBriefLoader.load(adminIds);

        List<AdminOperationLogVO> list = records.stream().map(entry -> new AdminOperationLogVO(
                entry.getId(),
                admins.getOrDefault(entry.getAdminId(), UserBriefLoader.deletedPlaceholder()),
                entry.getOperationType(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDetail(),
                entry.getIp(),
                entry.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    // ==================== 工具 ====================

    private AdminUserVO toAdminUserVO(User user) {
        return new AdminUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatar(),
                user.getRole(),
                user.getStatus(),
                user.getFollowingCount(),
                user.getFollowerCount(),
                user.getPostCount(),
                user.getCreateTime());
    }
}
