package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态视图对象。
 *
 * <p>{@code images} 是有序的图片 URL 列表（对应 {@code post_image.sort_order}），
 * 而不是让前端再去调一次接口。
 */
@Schema(description = "动态信息")
public record PostVO(

        @Schema(description = "动态 ID", example = "1")
        Long id,

        @Schema(description = "文本内容")
        String content,

        @Schema(description = "图片 URL 列表，按展示顺序排列，最多 9 张")
        List<String> images,

        @Schema(description = "点赞数", example = "10")
        Integer likeCount,

        @Schema(description = "评论数", example = "5")
        Integer commentCount,

        @Schema(description = "当前登录用户是否已点赞；游客恒为 false", example = "false")
        Boolean likedByMe,

        @Schema(description = "当前登录用户是否为作者，前端据此决定是否显示删除按钮", example = "false")
        Boolean mine,

        @Schema(description = "作者信息")
        UserBriefVO author,

        @Schema(description = "发布时间")
        LocalDateTime createTime
) {
}
