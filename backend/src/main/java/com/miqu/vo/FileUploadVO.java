package com.miqu.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件上传结果")
public record FileUploadVO(

        @Schema(description = "可直接用于 <img src> 的访问路径", example = "/uploads/image/2026/09/abc123.jpg")
        String url
) {
}
