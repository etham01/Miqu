package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通知列表查询参数")
public class NotificationQuery extends PageQuery {

    @Schema(description = "按类型过滤：1 关注 2 点赞 3 评论；不传表示全部",
            example = "2", allowableValues = {"1", "2", "3"})
    @Min(value = 1, message = "通知类型只能是 1、2 或 3")
    @Max(value = 3, message = "通知类型只能是 1、2 或 3")
    private Integer type;

    @Schema(description = "按已读状态过滤：0 未读 1 已读；不传表示全部", example = "0")
    @Min(value = 0, message = "已读状态只能是 0 或 1")
    @Max(value = 1, message = "已读状态只能是 0 或 1")
    private Integer isRead;
}
