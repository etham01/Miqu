package com.miqu.common;

/**
 * 业务常量。
 *
 * <p>所有长度上限、数量上限集中在此，既是 Bean Validation 注解的取值来源，
 * 也是 Service 层二次校验与测试用例的边界值来源。改一处即全局生效。
 */
public final class BizConstants {

    private BizConstants() {
    }

    // ---------- 用户 ----------
    public static final int USERNAME_MIN = 4;
    public static final int USERNAME_MAX = 20;
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 20;
    public static final int NICKNAME_MAX = 32;
    public static final int EMAIL_MAX = 64;
    public static final int BIO_MAX = 255;
    public static final int AVATAR_MAX = 255;

    /** 用户名允许的字符：字母、数字、下划线，且必须以字母开头。 */
    public static final String USERNAME_PATTERN = "^[A-Za-z][A-Za-z0-9_]*$";

    public static final String EMAIL_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    // ---------- 动态 ----------
    public static final int POST_CONTENT_MAX = 1000;
    public static final int MAX_POST_IMAGES = 9;

    // ---------- 评论 ----------
    public static final int COMMENT_CONTENT_MAX = 500;

    // ---------- 私信 ----------
    public static final int MESSAGE_CONTENT_MAX = 1000;
    public static final int MESSAGE_PREVIEW_LENGTH = 100;

    // ---------- 通知 ----------
    /** 通知中携带的内容快照长度上限。 */
    public static final int NOTIFICATION_SNAPSHOT_LENGTH = 50;

    // ---------- 举报 ----------
    public static final int REPORT_DETAIL_MAX = 255;

    // ---------- 分页 ----------
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_CHAT_PAGE_SIZE = 50;

    // ---------- 文件 ----------
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_IMAGE_URL_LENGTH = 255;
}
