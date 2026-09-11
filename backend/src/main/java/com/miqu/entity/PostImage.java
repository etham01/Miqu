package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 动态图片。对应 {@code post_image} 表。
 *
 * <p>物理删除：随动态一并清除，不做逻辑删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("post_image")
public class PostImage extends BaseCreateEntity {

    private Long postId;

    private String url;

    /** 展示顺序 0~8，同一动态内唯一（{@code uk_post_sort}）。 */
    private Integer sortOrder;
}
