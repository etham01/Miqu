package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：有创建/更新时间，但**没有逻辑删除**。
 *
 * <p>适用于 conversation / report 这类"可更新但不可删除"的表。
 *
 * <p><b>不要用 {@link BaseEntity} 代替它。</b> {@code BaseEntity} 带 {@code @TableLogic}，
 * MyBatis-Plus 会给每条 SQL 自动追加 {@code WHERE deleted = 0}；
 * 若对应的表没有 {@code deleted} 列，查询会直接抛
 * {@code Unknown column 'deleted' in 'where clause'}。
 *
 * <p>这类错误有个特点：**只要不查这张表就不会暴露**。所以新增实体时务必对照
 * {@code database/schema.sql} 确认表结构；{@code EntitySchemaConsistencyTest}
 * 会自动核对每个实体的逻辑删除标注与表列是否一致。
 */
@Data
public abstract class BaseEntityNoDelete implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
