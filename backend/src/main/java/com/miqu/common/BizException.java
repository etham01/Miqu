package com.miqu.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层遇到可预期的业务规则违例时抛出，由 {@code GlobalExceptionHandler}
 * 统一翻译成 {@link Result}。禁止用它包装系统级异常（那类直接抛原始异常走 500）。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义文案覆盖枚举默认文案。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }
}
