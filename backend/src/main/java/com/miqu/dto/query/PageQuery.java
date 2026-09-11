package com.miqu.dto.query;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询基类。
 *
 * <p>约束 page ≥ 1、size ∈ [1, 50]。超出范围直接返回 400 而不是静默重置为默认值——
 * 静默改写参数会让调用方以为生效了，也让接口自动化测试无法断言真实行为。
 *
 * <p>同时 {@code size} 的上限与 {@code PaginationInnerInterceptor.setMaxLimit} 形成双保险：
 * 一层在参数校验，一层在 SQL 层，防止有人绕过 DTO 直接构造查询。
 */
@Data
public class PageQuery {

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码必须大于 0")
    private Integer page = BizConstants.DEFAULT_PAGE;

    @Schema(description = "每页条数，1~50", example = "10", defaultValue = "10")
    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = BizConstants.MAX_PAGE_SIZE, message = "每页条数不能超过 " + BizConstants.MAX_PAGE_SIZE)
    private Integer size = BizConstants.DEFAULT_PAGE_SIZE;

    /** 非空页码，供 Service 直接使用。 */
    public long pageNum() {
        return page == null || page < 1 ? BizConstants.DEFAULT_PAGE : page;
    }

    /** 非空且已夹紧的每页条数。 */
    public long pageSize() {
        if (size == null || size < 1) {
            return BizConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, BizConstants.MAX_PAGE_SIZE);
    }
}
