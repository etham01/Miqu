package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户简要信息，嵌入在动态、评论、点赞列表等场景中。
 *
 * <p>刻意只保留展示所需的字段，且**不含邮箱**——它是非公开信息。
 */
@Schema(description = "用户简要信息")
public record UserBriefVO(

        @Schema(description = "用户 ID", example = "2")
        Long id,

        @Schema(description = "登录名", example = "test001")
        String username,

        @Schema(description = "昵称", example = "张三")
        String nickname,

        @Schema(description = "头像 URL")
        String avatar,

        @Schema(description = "个人简介")
        String bio
) {
}
