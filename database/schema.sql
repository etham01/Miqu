-- ============================================================
--  Miqu「觅取」社交系统 —— 数据库结构
--  MySQL 8.0+ / 9.x  ·  InnoDB  ·  utf8mb4
--
--  执行方式：
--    mysql -u root -p < schema.sql
--    mysql -u root -p < data.sql
--
--  设计约定：
--    1. 业务主表（user/post/comment/message/notification）含 deleted 逻辑删除字段
--    2. 关系表（follow/post_like/post_image）**不含 deleted**，取消关系即物理删除
--       —— 否则唯一键会被已软删的记录占用，导致"取消后无法重新点赞/关注"
--    3. 不建物理外键，用索引 + Service 层校验保证引用完整性
--    4. 时间统一 DATETIME(3)，精确到毫秒，避免分页排序不稳定
-- ============================================================

DROP DATABASE IF EXISTS `miqu`;
CREATE DATABASE `miqu`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `miqu`;

-- ------------------------------------------------------------
-- user 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(32)     NOT NULL                COMMENT '登录名，唯一',
    `password`        VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希，禁止明文',
    `nickname`        VARCHAR(32)     NOT NULL                COMMENT '昵称',
    `email`           VARCHAR(64)     NOT NULL                COMMENT '邮箱，唯一',
    `gender`          TINYINT         NOT NULL DEFAULT 0      COMMENT '性别 0未知 1男 2女',
    `birthday`        DATE            NULL                    COMMENT '生日',
    `bio`             VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '个人简介',
    `avatar`          VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '头像 URL',
    `role`            TINYINT         NOT NULL DEFAULT 1      COMMENT '角色 1=USER 2=ADMIN',
    `status`          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态 1正常 0禁用',
    `following_count` INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '关注数（冗余）',
    `follower_count`  INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '粉丝数（冗余）',
    `post_count`      INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '动态数（冗余）',
    `deleted`         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0正常 1已删除',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email`    (`email`),
    KEY `idx_nickname`    (`nickname`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';


-- ------------------------------------------------------------
-- follow 关注关系表（物理删除，无 deleted）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `follow`;
CREATE TABLE `follow` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `follower_id`  BIGINT UNSIGNED NOT NULL                COMMENT '关注者（粉丝）',
    `following_id` BIGINT UNSIGNED NOT NULL                COMMENT '被关注者',
    `create_time`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- 防重复关注的核心：并发下由 DB 兜底，Service 层"先查再插"只做快速失败
    UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
    KEY `idx_following` (`following_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系表';

-- ------------------------------------------------------------
-- post 动态表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post`;
CREATE TABLE `post` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL                COMMENT '作者',
    `content`       VARCHAR(1000)   NOT NULL DEFAULT ''     COMMENT '文本内容',
    `like_count`    INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '点赞数（冗余）',
    `comment_count` INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '评论数（冗余）',
    `image_count`   TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '图片数 0~9',
    `deleted`       TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `deleted`, `create_time` DESC),
    KEY `idx_time`      (`deleted`, `create_time` DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '动态表';

-- ------------------------------------------------------------
-- post_image 动态图片表（物理删除，无 deleted）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_image`;
CREATE TABLE `post_image` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属动态',
    `url`        VARCHAR(255)    NOT NULL                COMMENT '图片 URL',
    `sort_order` TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '展示顺序 0~8',
    `create_time` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_sort` (`post_id`, `sort_order`),
    KEY `idx_post` (`post_id`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '动态图片表';

-- ------------------------------------------------------------
-- post_like 点赞表（物理删除，无 deleted）
--   取消点赞必须物理 DELETE，否则 uk_post_user 被占用 → 无法再次点赞
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id`     BIGINT UNSIGNED NOT NULL                COMMENT '被点赞动态',
    `user_id`     BIGINT UNSIGNED NOT NULL                COMMENT '点赞用户',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '动态点赞表';

-- ------------------------------------------------------------
-- comment 评论表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id`          BIGINT UNSIGNED NOT NULL                COMMENT '所属动态',
    `user_id`          BIGINT UNSIGNED NOT NULL                COMMENT '评论者',
    `content`          VARCHAR(500)    NOT NULL                COMMENT '评论内容',
    `parent_id`        BIGINT UNSIGNED NULL                    COMMENT '父评论（预留多级评论）',
    `reply_to_user_id` BIGINT UNSIGNED NULL                    COMMENT '回复目标用户（预留）',
    `deleted`          TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_post_time` (`post_id`, `deleted`, `create_time`),
    KEY `idx_user`      (`user_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论表';

-- ------------------------------------------------------------
-- conversation 会话表（1:1）
--   强制 user1_id < user2_id，配合唯一键使 (A,B) 与 (B,A) 为同一条记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user1_id`             BIGINT UNSIGNED NOT NULL                COMMENT '参与者（较小 ID）',
    `user2_id`             BIGINT UNSIGNED NOT NULL                COMMENT '参与者（较大 ID）',
    `last_message_id`      BIGINT UNSIGNED NULL                    COMMENT '最后一条消息',
    `last_message_preview` VARCHAR(100)    NOT NULL DEFAULT ''     COMMENT '最后消息预览（截断）',
    `last_message_time`    DATETIME(3)     NULL                    COMMENT '最后消息时间',
    `user1_unread`         INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT 'user1 的未读数（权威值）',
    `user2_unread`         INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT 'user2 的未读数（权威值）',
    `create_time`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users` (`user1_id`, `user2_id`),
    KEY `idx_user1_time` (`user1_id`, `last_message_time` DESC),
    KEY `idx_user2_time` (`user2_id`, `last_message_time` DESC),
    CONSTRAINT `ck_user_order` CHECK (`user1_id` < `user2_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '私信会话表';

-- ------------------------------------------------------------
-- message 私信表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id` BIGINT UNSIGNED NOT NULL                COMMENT '所属会话',
    `sender_id`       BIGINT UNSIGNED NOT NULL                COMMENT '发送者',
    `receiver_id`     BIGINT UNSIGNED NOT NULL                COMMENT '接收者',
    `content`         VARCHAR(1000)   NOT NULL                COMMENT '消息内容',
    `is_read`         TINYINT         NOT NULL DEFAULT 0      COMMENT '是否已读 0未读 1已读',
    `read_time`       DATETIME(3)     NULL                    COMMENT '已读时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除（撤回，预留）',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_conv_id`        (`conversation_id`, `id` DESC),
    KEY `idx_receiver_read`  (`receiver_id`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '私信消息表';

-- ------------------------------------------------------------
-- notification 通知表
--   type: 1关注 2点赞 3评论（4私信保留不使用 —— 私信不写通知表，避免与消息未读重复）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL                COMMENT '接收者',
    `type`       TINYINT         NOT NULL                COMMENT '类型 1关注 2点赞 3评论',
    `actor_id`   BIGINT UNSIGNED NOT NULL                COMMENT '触发者',
    `post_id`    BIGINT UNSIGNED NULL                    COMMENT '相关动态',
    `comment_id` BIGINT UNSIGNED NULL                    COMMENT '相关评论',
    `message_id` BIGINT UNSIGNED NULL                    COMMENT '相关私信（预留）',
    `content`    VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '内容快照，防关联数据被删后断链',
    `is_read`    TINYINT         NOT NULL DEFAULT 0      COMMENT '是否已读',
    `deleted`    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
    `create_time` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- 一个索引同时覆盖"通知列表"与"未读数"两种查询
    KEY `idx_user_read_time` (`user_id`, `is_read`, `create_time` DESC),
    -- 取消点赞时按此索引定位并删除对应的点赞通知
    KEY `idx_user_actor_post_type` (`user_id`, `actor_id`, `post_id`, `type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知表';

-- ------------------------------------------------------------
-- report 举报表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `report`;
CREATE TABLE `report` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `reporter_id`   BIGINT UNSIGNED NOT NULL                COMMENT '举报人',
    `target_type`   TINYINT         NOT NULL                COMMENT '目标类型 1用户 2动态 3评论',
    `target_id`     BIGINT UNSIGNED NOT NULL                COMMENT '目标 ID（多态，无物理外键）',
    `reason_type`   TINYINT         NOT NULL                COMMENT '原因 1垃圾广告 2辱骂骚扰 3色情低俗 4违法违规 5其他',
    `reason_detail` VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '补充说明',
    `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态 0待处理 1已处理 2已驳回',
    `handler_id`    BIGINT UNSIGNED NULL                    COMMENT '处理人（管理员）',
    `handle_remark` VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '处理备注',
    `handle_time`   DATETIME(3)     NULL                    COMMENT '处理时间',
    `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reporter_target` (`reporter_id`, `target_type`, `target_id`),
    KEY `idx_status_time` (`status`, `create_time` DESC),
    KEY `idx_target`      (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '举报表';

-- ------------------------------------------------------------
-- admin_operation_log 管理员操作日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `admin_operation_log`;
CREATE TABLE `admin_operation_log` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id`       BIGINT UNSIGNED NOT NULL                COMMENT '管理员',
    `operation_type` VARCHAR(32)     NOT NULL                COMMENT '操作类型，如 DELETE_POST',
    `target_type`    TINYINT         NOT NULL                COMMENT '目标类型 1用户 2动态 3评论 4举报',
    `target_id`      BIGINT UNSIGNED NULL                    COMMENT '目标 ID',
    `detail`         VARCHAR(500)    NOT NULL DEFAULT ''     COMMENT '操作摘要（禁止写入密码等敏感信息）',
    `ip`             VARCHAR(45)     NOT NULL DEFAULT ''     COMMENT '操作 IP',
    `create_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_admin_time` (`admin_id`, `create_time` DESC),
    KEY `idx_type_time`  (`operation_type`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员操作日志';
