package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 动态。对应 {@code post} 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("post")
public class Post extends BaseEntity {

    private Long userId;

    private String content;

    /** 点赞数（冗余）。取消点赞时用 GREATEST(x-1,0) 防负数。 */
    private Integer likeCount;

    /** 评论数（冗余）。 */
    private Integer commentCount;

    /** 图片数，0~9。 */
    private Integer imageCount;
}
