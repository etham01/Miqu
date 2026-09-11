package com.miqu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.PageResult;
import com.miqu.common.enums.ReportActionEnum;
import com.miqu.common.enums.ReportStatusEnum;
import com.miqu.common.enums.TargetTypeEnum;
import com.miqu.common.enums.UserStatusEnum;
import com.miqu.dto.query.AdminReportQuery;
import com.miqu.dto.request.CreateReportRequest;
import com.miqu.dto.request.HandleReportRequest;
import com.miqu.entity.Comment;
import com.miqu.entity.Post;
import com.miqu.entity.Report;
import com.miqu.entity.User;
import com.miqu.mapper.CommentMapper;
import com.miqu.mapper.PostMapper;
import com.miqu.mapper.ReportMapper;
import com.miqu.mapper.UserMapper;
import com.miqu.service.AdminLogService;
import com.miqu.service.CommentService;
import com.miqu.service.PostService;
import com.miqu.service.ReportService;
import com.miqu.service.UserService;
import com.miqu.service.support.UserBriefLoader;
import com.miqu.vo.AdminReportVO;
import com.miqu.vo.ReportVO;
import com.miqu.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final int TARGET_PREVIEW_LENGTH = 40;
    private static final String DELETED_TARGET_HINT = "（内容已被删除）";

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final AdminLogService adminLogService;
    private final UserBriefLoader userBriefLoader;

    // ==================== 提交举报 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO create(Long userId, CreateReportRequest request) {
        userService.requireActiveUser(userId);

        Integer targetType = request.targetType();
        Long targetId = request.targetId();
        if (!TargetTypeEnum.isValid(targetType) || Objects.equals(targetType, TargetTypeEnum.REPORT.getCode())) {
            throw BizException.of(ErrorCode.INVALID_TARGET_TYPE);
        }

        // 多态外键无法靠数据库保证引用完整性，只能在这里按类型分支校验目标是否存在
        Long ownerId = resolveTargetOwner(targetType, targetId);
        if (userId.equals(ownerId)) {
            throw BizException.of(ErrorCode.CANNOT_REPORT_SELF);
        }

        // 快速失败；并发下真正的兜底是 uk_reporter_target 唯一键 → 409
        if (reportMapper.exists(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId))) {
            throw BizException.of(ErrorCode.ALREADY_REPORTED);
        }

        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReasonType(request.reasonType());
        report.setReasonDetail(request.reasonDetail() == null ? "" : request.reasonDetail().trim());
        report.setStatus(ReportStatusEnum.PENDING.getCode());
        reportMapper.insert(report);

        log.info("提交举报: reportId={}, reporterId={}, target=({}, {})",
                report.getId(), userId, targetType, targetId);
        return new ReportVO(report.getId(), targetType, targetId, report.getCreateTime());
    }

    // ==================== 管理后台 ====================

    @Override
    public PageResult<AdminReportVO> listForAdmin(AdminReportQuery query) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreateTime)
                .orderByDesc(Report::getId);
        if (query.getStatus() != null) {
            wrapper.eq(Report::getStatus, query.getStatus());
        }

        Page<Report> page = new Page<>(query.pageNum(), query.pageSize());
        IPage<Report> result = reportMapper.selectPage(page, wrapper);

        List<Report> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), List.of());
        }

        // 举报人与处理人一起批量加载
        Set<Long> userIds = new HashSet<>();
        records.forEach(r -> {
            userIds.add(r.getReporterId());
            if (r.getHandlerId() != null) {
                userIds.add(r.getHandlerId());
            }
        });
        Map<Long, UserBriefVO> users = userBriefLoader.load(userIds);

        // 被举报对象按类型分组批量解析，避免逐条查询
        Map<String, String> previews = resolveTargetPreviews(records);

        List<AdminReportVO> list = records.stream().map(report -> new AdminReportVO(
                report.getId(),
                users.getOrDefault(report.getReporterId(), UserBriefLoader.deletedPlaceholder()),
                report.getTargetType(),
                report.getTargetId(),
                previews.getOrDefault(targetKey(report.getTargetType(), report.getTargetId()),
                        DELETED_TARGET_HINT),
                report.getReasonType(),
                report.getReasonDetail(),
                report.getStatus(),
                report.getHandlerId() == null ? null
                        : users.getOrDefault(report.getHandlerId(), UserBriefLoader.deletedPlaceholder()),
                report.getHandleRemark(),
                report.getHandleTime(),
                report.getCreateTime()
        )).toList();

        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long adminId, Long reportId, HandleReportRequest request) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw BizException.of(ErrorCode.NOT_FOUND, "举报不存在");
        }
        // 已处理过的举报不能重复处理，否则处置动作会被执行两次
        if (!ReportStatusEnum.isPending(report.getStatus())) {
            throw BizException.of(ErrorCode.REPORT_ALREADY_HANDLED);
        }

        Integer status = request.status();
        ReportActionEnum action = parseAction(request.action());

        // "判定未违规"与"删除内容"自相矛盾，拦在入口比事后解释便宜
        if (Objects.equals(status, ReportStatusEnum.REJECTED.getCode())
                && action != ReportActionEnum.NONE) {
            throw BizException.of(ErrorCode.REJECT_WITH_ACTION_NOT_ALLOWED);
        }
        validateActionMatchesTarget(action, report.getTargetType());

        applyAction(adminId, action, report);

        Report update = new Report();
        update.setId(reportId);
        update.setStatus(status);
        update.setHandlerId(adminId);
        update.setHandleRemark(request.handleRemark() == null ? "" : request.handleRemark().trim());
        update.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(update);

        adminLogService.record(adminId, AdminLogService.OP_HANDLE_REPORT,
                TargetTypeEnum.REPORT.getCode(), reportId,
                "处理举报 #" + reportId + " → " + statusText(status) + "，处置动作：" + action.name());

        log.info("处理举报: reportId={}, adminId={}, status={}, action={}", reportId, adminId, status, action);
    }

    // ==================== 内部工具 ====================

    private Long resolveTargetOwner(Integer targetType, Long targetId) {
        if (Objects.equals(targetType, TargetTypeEnum.USER.getCode())) {
            return userService.requireVisibleUser(targetId).getId();
        }
        if (Objects.equals(targetType, TargetTypeEnum.POST.getCode())) {
            return postService.requirePost(targetId).getUserId();
        }
        if (Objects.equals(targetType, TargetTypeEnum.COMMENT.getCode())) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment == null) {
                throw BizException.of(ErrorCode.COMMENT_NOT_FOUND);
            }
            return comment.getUserId();
        }
        throw BizException.of(ErrorCode.INVALID_TARGET_TYPE);
    }

    private ReportActionEnum parseAction(String action) {
        try {
            return ReportActionEnum.of(action);
        } catch (IllegalArgumentException e) {
            throw BizException.of(ErrorCode.INVALID_REPORT_ACTION);
        }
    }

    /** 删除动态的动作只能用在动态类举报上，否则会出现"删掉了举报目标之外的东西"。 */
    private void validateActionMatchesTarget(ReportActionEnum action, Integer targetType) {
        boolean matches = switch (action) {
            case NONE -> true;
            case DELETE_POST -> Objects.equals(targetType, TargetTypeEnum.POST.getCode());
            case DELETE_COMMENT -> Objects.equals(targetType, TargetTypeEnum.COMMENT.getCode());
            case DISABLE_USER -> Objects.equals(targetType, TargetTypeEnum.USER.getCode());
        };
        if (!matches) {
            throw BizException.of(ErrorCode.REPORT_ACTION_MISMATCH);
        }
    }

    private void applyAction(Long adminId, ReportActionEnum action, Report report) {
        try {
            switch (action) {
                case NONE -> {
                    // 只记录结论，不处置内容
                }
                case DELETE_POST -> postService.delete(adminId, report.getTargetId());
                case DELETE_COMMENT -> commentService.delete(adminId, report.getTargetId());
                case DISABLE_USER -> userService.updateStatus(adminId, report.getTargetId(),
                        UserStatusEnum.DISABLED.getCode());
            }
        } catch (BizException e) {
            // 目标可能已被作者本人删除。举报仍应能正常结案，
            // 因此只在"目标不存在"这一种情况下放行，其余异常照常抛出
            ErrorCode code = e.getErrorCode();
            if (code == ErrorCode.POST_NOT_FOUND
                    || code == ErrorCode.COMMENT_NOT_FOUND
                    || code == ErrorCode.USER_NOT_FOUND) {
                log.info("举报的后置处置未执行（目标已不存在）: action={}, targetId={}, 原因={}",
                        action, report.getTargetId(), e.getMessage());
            } else {
                throw e;
            }
        }
    }

    /**
     * 批量解析被举报对象的摘要。
     *
     * <p>按 targetType 分组后各查一次，无论一页多少条都只发 3 次查询。
     * 已删除的目标查不到，调用方会回退到"内容已被删除"的提示文案。
     */
    private Map<String, String> resolveTargetPreviews(List<Report> reports) {
        Map<Integer, Set<Long>> idsByType = new HashMap<>();
        for (Report report : reports) {
            idsByType.computeIfAbsent(report.getTargetType(), k -> new HashSet<>())
                    .add(report.getTargetId());
        }

        Map<String, String> previews = new HashMap<>();

        Set<Long> userIds = idsByType.get(TargetTypeEnum.USER.getCode());
        if (userIds != null && !userIds.isEmpty()) {
            for (User user : userMapper.selectList(
                    new LambdaQueryWrapper<User>().in(User::getId, userIds))) {
                previews.put(targetKey(TargetTypeEnum.USER.getCode(), user.getId()),
                        "用户：" + user.getNickname() + "（@" + user.getUsername() + "）");
            }
        }

        Set<Long> postIds = idsByType.get(TargetTypeEnum.POST.getCode());
        if (postIds != null && !postIds.isEmpty()) {
            for (Post post : postMapper.selectList(
                    new LambdaQueryWrapper<Post>().in(Post::getId, postIds))) {
                previews.put(targetKey(TargetTypeEnum.POST.getCode(), post.getId()),
                        "动态：" + abbreviate(post.getContent()));
            }
        }

        Set<Long> commentIds = idsByType.get(TargetTypeEnum.COMMENT.getCode());
        if (commentIds != null && !commentIds.isEmpty()) {
            for (Comment comment : commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>().in(Comment::getId, commentIds))) {
                previews.put(targetKey(TargetTypeEnum.COMMENT.getCode(), comment.getId()),
                        "评论：" + abbreviate(comment.getContent()));
            }
        }

        return previews;
    }

    private String targetKey(Integer targetType, Long targetId) {
        return targetType + ":" + targetId;
    }

    private String abbreviate(String content) {
        if (!StringUtils.hasText(content)) {
            return "（无文本内容）";
        }
        String trimmed = content.trim();
        return trimmed.length() <= TARGET_PREVIEW_LENGTH
                ? trimmed
                : trimmed.substring(0, TARGET_PREVIEW_LENGTH) + "…";
    }

    private String statusText(Integer status) {
        return Objects.equals(status, ReportStatusEnum.HANDLED.getCode()) ? "已处理" : "已驳回";
    }
}
