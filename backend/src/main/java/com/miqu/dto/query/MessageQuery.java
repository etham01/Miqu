package com.miqu.dto.query;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 聊天记录查询参数（**游标分页**）。
 *
 * <p>刻意不用 offset 分页：聊天记录会不断从头部新增，
 * 用 {@code page=2} 翻历史消息时，期间新到的消息会把旧消息往后挤，
 * 导致翻页出现重复或跳过。改用"取 id 小于 beforeId 的 N 条"，
 * 无论期间新增多少消息，翻页结果都稳定。
 */
@Data
@Schema(description = "聊天记录查询参数")
public class MessageQuery {

    @Schema(description = """
            游标：返回 id 小于该值的消息（即更早的消息）。
            首次加载不传，之后传上一页最早一条消息的 id。
            """, example = "120")
    @Min(value = 1, message = "beforeId 必须大于 0")
    private Long beforeId;

    @Schema(description = "每页条数，1~50，默认 20", example = "20", defaultValue = "20")
    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = BizConstants.MAX_CHAT_PAGE_SIZE, message = "每页条数不能超过 50")
    private Integer size = 20;

    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return 20L;
        }
        return Math.min(size, BizConstants.MAX_CHAT_PAGE_SIZE);
    }
}
