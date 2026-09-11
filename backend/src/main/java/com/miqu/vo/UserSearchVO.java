package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户搜索结果项。
 *
 * <p>比 {@link UserBriefVO} 多出粉丝数与关注状态：
 * 搜索页需要在结果里直接展示"多少人关注他"以及"我是否已关注"，
 * 否则用户每点开一个人就要多一次请求。
 */
@Schema(description = "用户搜索结果")
public record UserSearchVO(

        @Schema(description = "用户 ID", example = "2")
        Long id,

        @Schema(description = "登录名", example = "test001")
        String username,

        @Schema(description = "昵称", example = "张三")
        String nickname,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "个人简介")
        String bio,

        @Schema(description = "粉丝数", example = "14")
        Integer followerCount,

        @Schema(description = "当前登录用户是否已关注；游客恒为 false", example = "false")
        Boolean followedByMe
) {
}
