package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 用户公开主页信息（查看他人时使用）。
 *
 * <p>与 {@link UserVO} 的区别：不含邮箱，但额外携带"我"与"TA"之间的关注关系，
 * 前端据此渲染关注按钮与"互相关注"标识。
 *
 * <p>游客访问时 {@code followedByMe} / {@code followingMe} / {@code mutual} 均为 false。
 */
@Schema(description = "用户主页信息")
public record UserProfileVO(

        @Schema(description = "用户 ID", example = "2")
        Long id,

        @Schema(description = "登录名", example = "test001")
        String username,

        @Schema(description = "昵称", example = "张三")
        String nickname,

        @Schema(description = "性别 0未知 1男 2女", example = "1")
        Integer gender,

        @Schema(description = "个人简介")
        String bio,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "关注数", example = "6")
        Integer followingCount,

        @Schema(description = "粉丝数", example = "14")
        Integer followerCount,

        @Schema(description = "动态数", example = "3")
        Integer postCount,

        @Schema(description = "当前登录用户是否关注了 TA", example = "false")
        Boolean followedByMe,

        @Schema(description = "TA 是否关注了当前登录用户", example = "true")
        Boolean followingMe,

        @Schema(description = "是否互相关注", example = "false")
        Boolean mutual,

        @Schema(description = "注册时间")
        LocalDateTime createTime
) {
}
