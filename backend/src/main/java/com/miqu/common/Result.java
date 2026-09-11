package com.miqu.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应体。
 *
 * <pre>
 * 成功：{ "code": 200, "message": "success", "data": { ... } }
 * 失败：{ "code": 400, "message": "用户名不能为空", "data": null }
 * </pre>
 */
@Schema(description = "统一响应体")
public record Result<T>(
        @Schema(description = "业务状态码，与 HTTP 语义对齐", example = "200")
        int code,

        @Schema(description = "提示信息", example = "success")
        String message,

        @Schema(description = "业务数据，失败时为 null")
        T data
) {

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 使用自定义文案覆盖枚举默认文案（用于携带具体字段的校验错误等场景）。 */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败但仍返回数据。
     *
     * <p>用于「失败本身也有信息量」的场景，比如健康检查：整体 DOWN 时，
     * 调用方仍然需要知道是哪个依赖挂了。
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message, data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
