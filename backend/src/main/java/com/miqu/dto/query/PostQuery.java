package com.miqu.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 首页动态流查询参数。
 *
 * <p>只需要"最新"与"关注"两种模式，不引入推荐算法。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "动态列表查询参数")
public class PostQuery extends PageQuery {

    public static final String TAB_LATEST = "latest";
    public static final String TAB_FOLLOWING = "following";

    @Schema(description = "列表模式：latest 最新动态（默认），following 我关注的人的动态",
            example = "latest", allowableValues = {TAB_LATEST, TAB_FOLLOWING})
    @Pattern(regexp = "latest|following", message = "tab 取值只能是 latest 或 following")
    private String tab = TAB_LATEST;

    public boolean isFollowingTab() {
        return TAB_FOLLOWING.equals(tab);
    }
}
