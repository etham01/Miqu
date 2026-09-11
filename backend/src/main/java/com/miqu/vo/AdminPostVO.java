package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台的动态视图。
 *
 * <p>只返回未删除的动态（逻辑删除由 MyBatis-Plus 自动过滤）——
 * 后台列表的用途是"管理现存内容"，翻已删内容属于审计范畴，交给操作日志。
 */
@Schema(description = "管理后台动态信息")
public record AdminPostVO(

        @Schema(description = "动态 ID", example = "1")
        Long id,

        @Schema(description = "文本内容")
        String content,

        @Schema(description = "图片 URL 列表")
        List<String> images,

        @Schema(description = "作者信息")
        UserBriefVO author,

        @Schema(description = "点赞数", example = "10")
        Integer likeCount,

        @Schema(description = "评论数", example = "5")
        Integer commentCount,

        @Schema(description = "发布时间")
        LocalDateTime createTime
) {
}
