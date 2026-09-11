package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.request.CommentCreateRequest;
import com.miqu.entity.Comment;
import com.miqu.entity.Post;
import com.miqu.entity.User;
import com.miqu.mapper.CommentMapper;
import com.miqu.mapper.PostMapper;
import com.miqu.security.CurrentUserHolder;
import com.miqu.service.CommentService;
import com.miqu.service.NotificationService;
import com.miqu.service.PostService;
import com.miqu.service.UserService;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.CommentVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserService userService;
    private final PostService postService;
    private final NotificationService notificationService;
    private final UserBriefLoader userBriefLoader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO create(Long userId, Long postId, CommentCreateRequest request) {
        User author = userService.requireActiveUser(userId);
        Post post = postService.requirePost(postId);

        String content = request.content().trim();

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        commentMapper.insert(comment);

        postMapper.incrCommentCount(postId);
        // 评论自己的动态不会产生通知，这条规则由 NotificationService 统一处理
        notificationService.notifyComment(userId, post.getUserId(), postId, comment.getId(), content);

        log.info("发表评论: commentId={}, postId={}, userId={}", comment.getId(), postId, userId);
        return new CommentVO(comment.getId(), postId, content, true,
                UserBriefLoader.toBrief(author), comment.getCreateTime());
    }

    @Override
    public PageResult<CommentVO> list(Long currentUserId, Long postId, PageQuery query) {
        postService.requirePost(postId);

        Page<Comment> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Comment> result = commentMapper.selectPage(page, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByAsc(Comment::getCreateTime)
                .orderByAsc(Comment::getId));

        List<Comment> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        Map<Long, UserBriefVO> authors = userBriefLoader.load(
                records.stream().map(Comment::getUserId).toList());

        List<CommentVO> list = records.stream().map(comment -> new CommentVO(
                comment.getId(),
                comment.getPostId(),
                comment.getContent(),
                currentUserId != null && currentUserId.equals(comment.getUserId()),
                authors.getOrDefault(comment.getUserId(), UserBriefLoader.deletedPlaceholder()),
                comment.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currentUserId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw BizException.of(ErrorCode.COMMENT_NOT_FOUND);
        }

        boolean isAuthor = comment.getUserId().equals(currentUserId);
        if (!isAuthor && !CurrentUserHolder.isAdmin()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }

        commentMapper.deleteById(commentId);

        // 动态已被删除时不再递减计数：那条动态的评论数已经没有意义，
        // 而且此时 post 查询会因逻辑删除返回 null
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null) {
            postMapper.decrCommentCount(comment.getPostId());
        }

        log.info("删除评论: commentId={}, postId={}, operatorId={}, 管理员={}",
                commentId, comment.getPostId(), currentUserId, CurrentUserHolder.isAdmin());
    }
}
