package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 动态点赞。对应 {@code post_like} 表。
 *
 * <p><b>必须物理删除。</b> 取消点赞后唯一键 {@code uk_post_user} 必须被释放，
 * 否则"取消点赞后可以再次点赞"这条业务规则无法实现。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("post_like")
public class PostLike extends BaseCreateEntity {

    private Long postId;

    private Long userId;
}
