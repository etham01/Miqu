package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务主表实体基类：带逻辑删除与创建/更新时间。
 *
 * <p>适用于 user / post / comment / message / notification / report。
 *
 * <p><b>关系表（follow / post_like / post_image）不要继承本类</b>——
 * 它们必须物理删除。若给关系表加上逻辑删除，取消点赞后唯一键
 * {@code uk_post_user} 仍被已软删的行占用，用户将无法再次点赞。
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 由 MetaObjectHandler 自动填充，禁止在 Service 中手写。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 由 MetaObjectHandler 自动填充，禁止在 Service 中手写。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     * {@code select = false} 让该字段不出现在 SELECT 列表中，避免泄漏到前端。
     */
    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
