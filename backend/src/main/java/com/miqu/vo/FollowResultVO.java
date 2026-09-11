package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 关注 / 取消关注的结果。
 *
 * <p>回传被关注者的最新粉丝数，前端可直接用它更新页面上的数字，
 * 省掉一次"关注完再查一遍用户信息"的往返。
 */
@Schema(description = "关注操作结果")
public record FollowResultVO(

        @Schema(description = "操作后的关注状态：true 已关注，false 未关注", example = "true")
        Boolean following,

        @Schema(description = "被关注者的最新粉丝数", example = "15")
        Integer followerCount
) {
}
