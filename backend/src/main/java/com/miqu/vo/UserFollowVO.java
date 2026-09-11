package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 关注列表 / 粉丝列表中的一行。
 *
 * <p>比 {@link UserBriefVO} 多出 {@code followedByMe}：
 * 在"我的粉丝"列表里，它决定要不要显示"回关"按钮；
 * 在"我的关注"列表里，它恒为 true。没有这个字段前端就只能再逐个查一次。
 */
@Schema(description = "关注/粉丝列表项")
public record UserFollowVO(

        @Schema(description = "用户 ID", example = "3")
        Long id,

        @Schema(description = "登录名", example = "test002")
        String username,

        @Schema(description = "昵称", example = "李四")
        String nickname,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "个人简介")
        String bio,

        @Schema(description = "当前登录用户是否关注了此人", example = "true")
        Boolean followedByMe,

        @Schema(description = "建立关注关系的时间")
        LocalDateTime followTime
) {
}
