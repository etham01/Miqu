package com.miqu.exception;

import com.miqu.common.BizException;
import com.miqu.common.ErrorCode;
import com.miqu.common.Result;
import com.miqu.config.MiquProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>分层原则：**响应对用户友好，日志对开发者友好**。
 * 系统异常的完整堆栈只进日志，绝不回传前端（避免泄漏内部结构）。
 *
 * <p>注意它有**一条明确的例外**：{@code /uploads/**} 下缺失的静态资源返回真正的
 * HTTP 404，而不是"HTTP 200 + body.code=404"。理由见 {@link #handleNoResource}。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MiquProperties properties;

    // ================== 业务异常 ==================

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        // 业务异常属于预期内分支，用 debug 级别，避免污染生产日志
        log.debug("业务异常 [{}] {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    // ================== 参数校验 ==================

    /** @RequestBody 上的 @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    /** 表单/查询参数绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    /** 方法参数上的 @Validated（如 @RequestParam 约束）失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "参数格式不正确：" + e.getName());
    }

    /** 请求体不是合法 JSON，或字段类型不匹配。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.debug("请求体解析失败：{}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, "请求体格式不正确");
    }

    // ================== 数据库约束 ==================

    /**
     * 唯一键冲突。
     *
     * <p>这是**并发场景下防重复的唯一正确兜底**：Service 层的"先查再插"只是快速失败，
     * 真正拦住重复数据的是数据库唯一键。而唯一键冲突默认会变成 500，
     * 会让"重复关注应返回 409"这类接口测试断言失败，所以必须按索引名翻译成业务错误码。
     *
     * <p>索引名与 {@code database/schema.sql} 一一对应，改索引名时此处必须同步。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        String raw = String.valueOf(e.getMessage());
        ErrorCode errorCode = switch (resolveConstraint(raw)) {
            case UK_USERNAME -> ErrorCode.USERNAME_EXISTS;
            case UK_EMAIL -> ErrorCode.EMAIL_EXISTS;
            case UK_FOLLOWER_FOLLOWING -> ErrorCode.ALREADY_FOLLOWED;
            case UK_POST_USER -> ErrorCode.ALREADY_LIKED;
            case UK_USERS -> ErrorCode.CONFLICT;
            case UK_REPORTER_TARGET -> ErrorCode.ALREADY_REPORTED;
            case UNKNOWN -> ErrorCode.CONFLICT;
        };
        if (errorCode == ErrorCode.CONFLICT) {
            // 未识别的约束：说明有新增索引没在这里登记，需要开发者关注
            log.warn("未映射的唯一约束冲突：{}", raw);
        } else {
            log.debug("唯一约束冲突映射为 {}：{}", errorCode.name(), errorCode.getMessage());
        }
        return Result.fail(errorCode);
    }

    private Constraint resolveConstraint(String message) {
        if (message.contains("uk_username")) return Constraint.UK_USERNAME;
        if (message.contains("uk_email")) return Constraint.UK_EMAIL;
        if (message.contains("uk_follower_following")) return Constraint.UK_FOLLOWER_FOLLOWING;
        if (message.contains("uk_post_user")) return Constraint.UK_POST_USER;
        if (message.contains("uk_users")) return Constraint.UK_USERS;
        if (message.contains("uk_reporter_target")) return Constraint.UK_REPORTER_TARGET;
        return Constraint.UNKNOWN;
    }

    private enum Constraint {
        UK_USERNAME, UK_EMAIL, UK_FOLLOWER_FOLLOWING, UK_POST_USER,
        UK_USERS, UK_REPORTER_TARGET, UNKNOWN
    }

    // ================== 路由 / 上传 ==================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(405, "请求方法不支持：" + e.getMethod());
    }

    /**
     * 请求的 Content-Type 与接口声明的不一致。
     *
     * <p>典型场景：`POST /api/files/image` 只接受 multipart，客户端却发了
     * `application/x-www-form-urlencoded`。这属于**客户端请求错误**，
     * 不落到兜底分支返回 500 —— 否则接口测试无法区分"服务端炸了"和"请求发错了"。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "不支持的请求类型：" + e.getContentType());
    }

    /**
     * 静态资源或接口路径不存在。
     *
     * <p>这里**按路径分两种语义**：
     *
     * <ul>
     *   <li><b>上传的静态资源</b>（{@code /uploads/**}）返回**真正的 HTTP 404**。
     *       浏览器 {@code <img>} 与 CDN 靠状态码判断"取不到"，包成 200 会让
     *       {@code <img>} 拿到一段 JSON 去解码（必然失败），也会让缓存层把
     *       "不存在的资源"当成一次成功响应。</li>
     *   <li><b>业务接口</b>（{@code /api/**}）沿用项目约定——「HTTP 恒 200，
     *       结果看响应体 {@code code}」——不改变既有对外契约。</li>
     * </ul>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e,
                                                         HttpServletRequest request) {
        Result<Void> body = Result.fail(ErrorCode.NOT_FOUND, "请求的资源不存在");

        String staticPrefix = properties.getUpload().getUrlPrefix() + "/";
        if (request != null && request.getRequestURI().startsWith(staticPrefix)) {
            log.debug("静态资源不存在：{}", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(ErrorCode.FILE_TOO_LARGE);
    }

    /**
     * multipart 请求里缺少接口声明的 part（如 `POST /api/files/image` 没带 `file` 字段）。
     *
     * <p>注意它**不是** {@link MissingServletRequestParameterException}：
     * `@RequestParam MultipartFile` 由 Multipart 解析器处理，缺字段时抛的是
     * `MissingServletRequestPartException`，父类为 ServletException，
     * 不额外登记就会掉进兜底分支变成 500。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<Void> handleMissingPart(MissingServletRequestPartException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少必要参数：" + e.getRequestPartName());
    }

    // ================== 兜底 ==================

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    /** 便于在日志里输出字段错误的完整列表（仅在需要时使用）。 */
    static String joinFieldErrors(BindException e) {
        return e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
    }
}
