package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 评论。对应 {@code comment} 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("comment")
public class Comment extends BaseEntity {

    private Long postId;

    private Long userId;

    private String content;

    /** 父评论，一期不启用（预留多级评论）。 */
    private Long parentId;

    /** 回复目标用户，一期不启用（预留）。 */
    private Long replyToUserId;
}
