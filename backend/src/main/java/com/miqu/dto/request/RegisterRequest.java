package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 注册请求。
 *
 * <p>约束文案即接口返回的 {@code message}，所以写得面向调用方而不是开发者。
 * 测试用例构造边界值时可直接对照这些约束。
 */
@Schema(description = "注册请求")
public record RegisterRequest(

        @Schema(description = "登录名，字母开头，可含数字与下划线", example = "test001")
        @NotBlank(message = "用户名不能为空")
        @Size(min = BizConstants.USERNAME_MIN, max = BizConstants.USERNAME_MAX,
                message = "用户名长度必须在 4~20 个字符之间")
        @Pattern(regexp = BizConstants.USERNAME_PATTERN,
                message = "用户名必须以字母开头，且只能包含字母、数字和下划线")
        String username,

        @Schema(description = "密码，6~20 位", example = "123456")
        @NotBlank(message = "密码不能为空")
        @Size(min = BizConstants.PASSWORD_MIN, max = BizConstants.PASSWORD_MAX,
                message = "密码长度必须在 6~20 个字符之间")
        String password,

        @Schema(description = "昵称", example = "张三")
        @NotBlank(message = "昵称不能为空")
        @Size(max = BizConstants.NICKNAME_MAX, message = "昵称长度不能超过 32 个字符")
        String nickname,

        @Schema(description = "邮箱", example = "test001@miqu.com")
        @NotBlank(message = "邮箱不能为空")
        @Size(max = BizConstants.EMAIL_MAX, message = "邮箱长度不能超过 64 个字符")
        @Email(message = "邮箱格式不正确")
        String email,

        @Schema(description = "性别 0未知 1男 2女，不传默认 0", example = "1")
        @Min(value = 0, message = "性别取值只能是 0、1 或 2")
        @Max(value = 2, message = "性别取值只能是 0、1 或 2")
        Integer gender,

        @Schema(description = "生日，格式 yyyy-MM-dd", example = "1998-03-15")
        LocalDate birthday,

        @Schema(description = "个人简介")
        @Size(max = BizConstants.BIO_MAX, message = "个人简介长度不能超过 255 个字符")
        String bio,

        @Schema(description = "头像 URL，可先调用上传接口获得")
        @Size(max = BizConstants.AVATAR_MAX, message = "头像地址长度不能超过 255 个字符")
        String avatar
) {
}
