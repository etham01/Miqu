package com.miqu.common;

import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>业务码与 HTTP 语义对齐，便于接口自动化测试直接断言 code：
 * 400 参数错误 / 401 未认证 / 403 无权限 / 404 不存在 / 409 冲突 / 423 账号禁用 / 500 系统异常。
 *
 * <p>约定：同名业务语义只在这里定义一次，禁止在 Service 里硬编码提示文案。
 */
@Getter
public enum ErrorCode {

    // ---------- 通用 ----------
    SUCCESS(200, "success"),
    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录状态已失效"),
    FORBIDDEN(403, "无权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "系统异常，请稍后重试"),

    // ---------- 认证 / 用户 ----------
    INVALID_CREDENTIALS(401, "用户名或密码错误"),
    USER_DISABLED(423, "账号已被禁用，请联系管理员"),
    USER_NOT_FOUND(404, "用户不存在"),
    USERNAME_EXISTS(409, "用户名已被占用"),
    EMAIL_EXISTS(409, "邮箱已被注册"),
    OLD_PASSWORD_MISMATCH(400, "原密码不正确"),
    PASSWORD_SAME_AS_OLD(400, "新密码不能与原密码相同"),
    CANNOT_OPERATE_SELF(400, "不能对自己执行该操作"),

    // ---------- 关注 ----------
    CANNOT_FOLLOW_SELF(400, "不能关注自己"),
    ALREADY_FOLLOWED(409, "已经关注过该用户"),
    NOT_FOLLOWED(404, "尚未关注该用户"),

    // ---------- 动态 ----------
    POST_NOT_FOUND(404, "动态不存在或已被删除"),
    EMPTY_POST(400, "动态内容与图片不能同时为空"),
    TOO_MANY_IMAGES(400, "最多只能上传 9 张图片"),
    INVALID_IMAGE_URL(400, "图片地址不合法，请先通过上传接口获取"),
    ALREADY_LIKED(409, "已经点赞过该动态"),
    NOT_LIKED(404, "尚未点赞该动态"),

    // ---------- 评论 ----------
    COMMENT_NOT_FOUND(404, "评论不存在或已被删除"),
    EMPTY_COMMENT(400, "评论内容不能为空"),

    // ---------- 私信 ----------
    CONVERSATION_NOT_FOUND(404, "会话不存在"),
    NOT_CONVERSATION_MEMBER(403, "你不是该会话的参与者"),
    CANNOT_MESSAGE_SELF(400, "不能给自己发送私信"),

    // ---------- 通知 ----------
    NOTIFICATION_NOT_FOUND(404, "通知不存在"),

    // ---------- 举报 ----------
    ALREADY_REPORTED(409, "你已经举报过该内容"),
    CANNOT_REPORT_SELF(400, "不能举报自己"),
    INVALID_TARGET_TYPE(400, "举报目标类型不合法"),
    REPORT_ALREADY_HANDLED(409, "该举报已被处理"),
    REPORT_ACTION_MISMATCH(400, "处置动作与举报目标类型不匹配"),
    REJECT_WITH_ACTION_NOT_ALLOWED(400, "驳回举报时不能同时执行处置动作"),

    // ---------- 管理后台 ----------
    CANNOT_DISABLE_ADMIN(403, "不能禁用管理员账号"),
    INVALID_REPORT_ACTION(400, "不支持的处置动作"),

    // ---------- 文件 ----------
    FILE_EMPTY(400, "上传文件不能为空"),
    FILE_TOO_LARGE(400, "图片大小不能超过 5MB"),
    FILE_TYPE_NOT_ALLOWED(400, "仅支持 jpg / png / gif / webp 格式的图片");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
