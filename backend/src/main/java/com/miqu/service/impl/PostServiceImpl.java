package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizConstants;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.config.MiquProperties;
import com.miqu.dto.query.PageQuery;
import com.miqu.dto.query.PostQuery;
import com.miqu.dto.request.PostCreateRequest;
import com.miqu.entity.Comment;
import com.miqu.entity.Post;
import com.miqu.entity.PostImage;
import com.miqu.entity.PostLike;
import com.miqu.entity.User;
import com.miqu.mapper.CommentMapper;
import com.miqu.mapper.PostImageMapper;
import com.miqu.mapper.PostLikeMapper;
import com.miqu.mapper.PostMapper;
import com.miqu.mapper.UserMapper;
import com.miqu.security.CurrentUserHolder;
import com.miqu.service.NotificationService;
import com.miqu.service.PostService;
import com.miqu.service.UserService;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.LikeResultVO;
import com.miqu.vo.PostVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostLikeMapper postLikeMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final UserBriefLoader userBriefLoader;
    private final MiquProperties properties;

    // ==================== 发布 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO create(Long userId, PostCreateRequest request) {
        User author = userService.requireActiveUser(userId);

        String content = request.content() == null ? "" : request.content().trim();
        List<String> images = normalizeImages(request.images());

        // 跨字段规则："内容为空且没有图片"才拒绝；两者有一即可
        if (content.isEmpty() && images.isEmpty()) {
            throw BizException.of(ErrorCode.EMPTY_POST);
        }
        if (images.size() > BizConstants.MAX_POST_IMAGES) {
            throw BizException.of(ErrorCode.TOO_MANY_IMAGES);
        }
        validateImageUrls(images);

        Post post = new Post();
        post.setUserId(userId);
        post.setContent(content);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setImageCount(images.size());
        postMapper.insert(post);

        for (int i = 0; i < images.size(); i++) {
            PostImage image = new PostImage();
            image.setPostId(post.getId());
            image.setUrl(images.get(i));
            image.setSortOrder(i);
            postImageMapper.insert(image);
        }

        userMapper.incrPostCount(userId);

        log.info("发布动态: postId={}, userId={}, imageCount={}", post.getId(), userId, images.size());
        return new PostVO(post.getId(), content, images, 0, 0, false, true,
                UserBriefLoader.toBrief(author), post.getCreateTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long currentUserId, Long postId) {
        Post post = requirePost(postId);

        boolean isAuthor = post.getUserId().equals(currentUserId);
        if (!isAuthor && !CurrentUserHolder.isAdmin()) {
            throw BizException.of(ErrorCode.FORBIDDEN);
        }

        // 四类关联数据的处理方式不同，各自的理由见 database/schema.sql 的注释：
        postMapper.deleteById(postId);                    // 动态：逻辑删除
        commentMapper.delete(new LambdaQueryWrapper<Comment>()      // 评论：逻辑删除
                .eq(Comment::getPostId, postId));
        postImageMapper.delete(new LambdaQueryWrapper<PostImage>()  // 图片：物理删除
                .eq(PostImage::getPostId, postId));
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()    // 点赞：物理删除
                .eq(PostLike::getPostId, postId));

        userMapper.decrPostCount(post.getUserId());
        notificationService.removePostNotifications(postId);

        log.info("删除动态: postId={}, operatorId={}, 作者={}, 管理员={}",
                postId, currentUserId, post.getUserId(), CurrentUserHolder.isAdmin());
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<PostVO> listFeed(Long currentUserId, PostQuery query) {
        Page<Post> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Post> result;

        if (query.isFollowingTab()) {
            if (currentUserId == null) {
                throw BizException.of(ErrorCode.UNAUTHORIZED);
            }
            result = postMapper.selectFollowingFeed(page, currentUserId);
        } else {
            // 排序必须带 id 兜底：create_time 精确到毫秒仍可能撞车，
            // 只按时间排序会让分页出现记录重复或丢失
            result = postMapper.selectPage(page, new LambdaQueryWrapper<Post>()
                    .orderByDesc(Post::getCreateTime)
                    .orderByDesc(Post::getId));
        }

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                assemble(result.getRecords(), currentUserId));
    }

    @Override
    public PageResult<PostVO> listByUser(Long currentUserId, Long targetUserId, PageQuery query) {
        userService.requireVisibleUser(targetUserId);

        Page<Post> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Post> result = postMapper.selectPage(page, new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, targetUserId)
                .orderByDesc(Post::getCreateTime)
                .orderByDesc(Post::getId));

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                assemble(result.getRecords(), currentUserId));
    }

    @Override
    public PostVO getDetail(Long currentUserId, Long postId) {
        Post post = requirePost(postId);
        return assemble(List.of(post), currentUserId).get(0);
    }

    @Override
    public PageResult<UserBriefVO> listLikes(Long postId, PageQuery query) {
        requirePost(postId);

        Page<PostLike> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<PostLike> result = postLikeMapper.selectPage(page, new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .orderByDesc(PostLike::getCreateTime)
                .orderByDesc(PostLike::getId));

        List<Long> userIds = result.getRecords().stream().map(PostLike::getUserId).toList();
        Map<Long, UserBriefVO> users = userBriefLoader.load(userIds);
        List<UserBriefVO> list = userIds.stream()
                .map(id -> users.getOrDefault(id, UserBriefLoader.deletedPlaceholder()))
                .toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    // ==================== 点赞 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResultVO like(Long userId, Long postId) {
        userService.requireActiveUser(userId);
        Post post = requirePost(postId);

        if (postLikeMapper.exists(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId))) {
            throw BizException.of(ErrorCode.ALREADY_LIKED);
        }

        PostLike postLike = new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);
        // 并发下这里可能抛 DuplicateKeyException，由 GlobalExceptionHandler
        // 按索引名 uk_post_user 翻译成同一个 409
        postLikeMapper.insert(postLike);

        postMapper.incrLikeCount(postId);
        notificationService.notifyLike(userId, post.getUserId(), postId);

        log.info("点赞: postId={}, userId={}", postId, userId);
        return new LikeResultVO(true, freshLikeCount(postId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeResultVO unlike(Long userId, Long postId) {
        Post post = requirePost(postId);

        // 物理删除，立刻释放 uk_post_user，用户才能再次点赞
        int deleted = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId));
        if (deleted == 0) {
            throw BizException.of(ErrorCode.NOT_LIKED);
        }

        postMapper.decrLikeCount(postId);
        notificationService.removeLikeNotification(userId, post.getUserId(), postId);

        log.info("取消点赞: postId={}, userId={}", postId, userId);
        return new LikeResultVO(false, freshLikeCount(postId));
    }

    // ==================== 内部工具 ====================

    @Override
    public Post requirePost(Long postId) {
        if (postId == null) {
            throw BizException.of(ErrorCode.POST_NOT_FOUND);
        }
        // 逻辑删除由 MyBatis-Plus 自动过滤，查不到即为已删除
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.of(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    /**
     * 批量组装 PostVO。
     *
     * <p>无论一页多少条，只发 3 次查询（作者、图片、我的点赞状态），
     * 避免逐条回填造成的 N+1。
     */
    private List<PostVO> assemble(List<Post> posts, Long currentUserId) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, UserBriefVO> authors = userBriefLoader.load(
                posts.stream().map(Post::getUserId).toList());

        Map<Long, List<String>> imageMap = postImageMapper.selectList(
                        new LambdaQueryWrapper<PostImage>()
                                .in(PostImage::getPostId, postIds)
                                .orderByAsc(PostImage::getSortOrder))
                .stream()
                .collect(Collectors.groupingBy(PostImage::getPostId,
                        Collectors.mapping(PostImage::getUrl, Collectors.toList())));

        Set<Long> likedPostIds = Collections.emptySet();
        if (currentUserId != null) {
            likedPostIds = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                            .eq(PostLike::getUserId, currentUserId)
                            .in(PostLike::getPostId, postIds))
                    .stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());
        }

        final Set<Long> liked = likedPostIds;
        return posts.stream().map(post -> new PostVO(
                post.getId(),
                post.getContent(),
                imageMap.getOrDefault(post.getId(), List.of()),
                post.getLikeCount(),
                post.getCommentCount(),
                liked.contains(post.getId()),
                currentUserId != null && currentUserId.equals(post.getUserId()),
                authors.getOrDefault(post.getUserId(), UserBriefLoader.deletedPlaceholder()),
                post.getCreateTime()
        )).toList();
    }

    /** 去掉空白项并 trim，让"只传了空字符串"与"没传"等价。 */
    private List<String> normalizeImages(List<String> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    /**
     * 图片地址必须来自本项目的上传接口。
     *
     * <p>否则用户可以把任意外链（甚至是一个追踪像素、一张违规图）塞进动态，
     * 服务器成了别人的图床，内容审核也无从下手。
     */
    private void validateImageUrls(List<String> images) {
        String prefix = properties.getUpload().getUrlPrefix() + "/";
        for (String url : images) {
            if (!url.startsWith(prefix)) {
                throw BizException.of(ErrorCode.INVALID_IMAGE_URL);
            }
        }
    }

    /**
     * 重新读取点赞数。
     *
     * <p>不能拿进入方法时查到的值加减：那可能已经被并发请求改过。
     * 上面刚执行过 UPDATE，MyBatis 会清空一级缓存，因此这次 SELECT 拿到的是数据库最新值。
     */
    private int freshLikeCount(Long postId) {
        Post post = postMapper.selectById(postId);
        return post == null || post.getLikeCount() == null ? 0 : post.getLikeCount();
    }
}
