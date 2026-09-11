package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当前登录用户的完整信息。
 *
 * <p>只有本人可见，因此包含邮箱等非公开字段。
 * **绝不包含密码**——实体与 VO 严格分离，是防止密码泄漏的最后一道防线。
 *
 * <p>id 声明为 Long，但序列化后被 JacksonConfig 转成字符串输出。
 */
@Schema(description = "用户信息")
public record UserVO(

        @Schema(description = "用户 ID（字符串形式，避免前端精度丢失）", example = "2")
        Long id,

        @Schema(description = "登录名", example = "test001")
        String username,

        @Schema(description = "昵称", example = "张三")
        String nickname,

        @Schema(description = "邮箱", example = "test001@miqu.com")
        String email,

        @Schema(description = "性别 0未知 1男 2女", example = "1")
        Integer gender,

        @Schema(description = "生日", example = "1998-03-15")
        LocalDate birthday,

        @Schema(description = "个人简介")
        String bio,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "角色 1普通用户 2管理员", example = "1")
        Integer role,

        @Schema(description = "关注数", example = "5")
        Integer followingCount,

        @Schema(description = "粉丝数", example = "12")
        Integer followerCount,

        @Schema(description = "动态数", example = "3")
        Integer postCount,

        @Schema(description = "注册时间", example = "2026-06-01 10:00:00")
        LocalDateTime createTime
) {
}
