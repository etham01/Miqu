package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "点赞操作结果")
public record LikeResultVO(

        @Schema(description = "操作后的点赞状态：true 已点赞，false 未点赞", example = "true")
        Boolean liked,

        @Schema(description = "动态的最新点赞数", example = "11")
        Integer likeCount
) {
}
