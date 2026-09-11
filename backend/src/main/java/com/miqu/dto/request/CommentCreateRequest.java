package com.miqu.dto.request;

import com.miqu.common.BizConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "发表评论请求")
public record CommentCreateRequest(

        @Schema(description = "评论内容", example = "构图很棒，第三张尤其好看。")
        @NotBlank(message = "评论内容不能为空")
        @Size(max = BizConstants.COMMENT_CONTENT_MAX, message = "评论内容不能超过 500 个字符")
        String content
) {
}
