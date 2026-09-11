package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布动态请求。
 *
 * <p>图片采用"先上传拿 URL，再提交 URL 列表"的两阶段模式：
 * 上传接口只负责存储与格式校验，业务接口只接收 URL，两者解耦且都便于接口测试。
 *
 * <p>{@code content} 与 {@code images} 至少要有一个非空，
 * 这条跨字段规则由 Service 校验（Bean Validation 的单字段注解表达不了）。
 */
@Schema(description = "发布动态请求")
public record PostCreateRequest(

        @Schema(description = "文本内容，可为空（纯图片动态）")
        @Size(max = BizConstants.POST_CONTENT_MAX, message = "动态内容不能超过 1000 个字符")
        String content,

        @Schema(description = "图片 URL 列表，按顺序展示，最多 9 张，由上传接口返回")
        @Size(max = BizConstants.MAX_POST_IMAGES, message = "最多只能上传 9 张图片")
        List<String> images
) {
}
