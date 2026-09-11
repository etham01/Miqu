package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "修改密码请求")
public record ChangePasswordRequest(

        @Schema(description = "原密码", example = "123456")
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @Schema(description = "新密码，6~20 位", example = "654321")
        @NotBlank(message = "新密码不能为空")
        @Size(min = BizConstants.PASSWORD_MIN, max = BizConstants.PASSWORD_MAX,
                message = "新密码长度必须在 6~20 个字符之间")
        String newPassword
) {
}
