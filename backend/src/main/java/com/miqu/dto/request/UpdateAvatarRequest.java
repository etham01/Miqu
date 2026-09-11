package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改头像请求。
 *
 * <p>采用"先上传拿 URL，再提交 URL"的两阶段模式，与发布动态的图片处理保持一致：
 * 上传接口只负责存储与格式校验，业务接口只接收 URL，两者解耦且都便于接口测试。
 */
@Schema(description = "修改头像请求")
public record UpdateAvatarRequest(

        @Schema(description = "头像 URL，由上传接口返回", example = "/uploads/image/2026/09/xxx.jpg")
        @NotBlank(message = "头像地址不能为空")
        @Size(max = BizConstants.AVATAR_MAX, message = "头像地址长度不能超过 255 个字符")
        String avatar
) {
}
