package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改个人资料请求。
 *
 * <p>刻意**不包含 username 与 email**：这两个字段有唯一约束，允许修改会把
 * "改到一半冲突"的复杂情况引入进来（用户名改了但邮箱冲突，要不要回滚？）。
 * 本期不支持修改，是产品决策而非遗漏。
 *
 * <p>所有字段均可为空表示"不修改"，只有显式传入的字段才会被更新。
 */
@Schema(description = "修改个人资料请求")
public record UpdateProfileRequest(

        @Schema(description = "昵称")
        @NotBlank(message = "昵称不能为空")
        @Size(max = BizConstants.NICKNAME_MAX, message = "昵称长度不能超过 32 个字符")
        String nickname,

        @Schema(description = "性别 0未知 1男 2女")
        @Min(value = 0, message = "性别取值只能是 0、1 或 2")
        @Max(value = 2, message = "性别取值只能是 0、1 或 2")
        Integer gender,

        @Schema(description = "生日，格式 yyyy-MM-dd")
        LocalDate birthday,

        @Schema(description = "个人简介")
        @Size(max = BizConstants.BIO_MAX, message = "个人简介长度不能超过 255 个字符")
        String bio
) {
}
