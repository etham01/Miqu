package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 关注关系。对应 {@code follow} 表。
 *
 * <p>继承 {@link BaseCreateEntity}：<b>物理删除</b>。
 * 取消关注即 DELETE，否则 {@code uk_follower_following} 会被已软删的行占用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("follow")
public class Follow extends BaseCreateEntity {

    /** 关注者（粉丝）。 */
    private Long followerId;

    /** 被关注者。 */
    private Long followingId;
}
