package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理后台的用户视图。
 *
 * <p>与 {@link UserVO} 的区别：包含 {@code status}（管理员最关心的字段），
 * 但同样**不含密码**。
 */
@Schema(description = "管理后台用户信息")
public record AdminUserVO(

        @Schema(description = "用户 ID", example = "2")
        Long id,

        @Schema(description = "登录名", example = "test001")
        String username,

        @Schema(description = "昵称", example = "张三")
        String nickname,

        @Schema(description = "邮箱", example = "test001@miqu.com")
        String email,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "角色 1 普通用户 2 管理员", example = "1")
        Integer role,

        @Schema(description = "状态 1 正常 0 禁用", example = "1")
        Integer status,

        @Schema(description = "关注数", example = "6")
        Integer followingCount,

        @Schema(description = "粉丝数", example = "14")
        Integer followerCount,

        @Schema(description = "动态数", example = "3")
        Integer postCount,

        @Schema(description = "注册时间")
        LocalDateTime createTime
) {
}
