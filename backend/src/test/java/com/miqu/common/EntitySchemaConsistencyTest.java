package com.miqu.common;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.miqu.entity.AdminOperationLog;
import com.miqu.entity.Comment;
import com.miqu.entity.Conversation;
import com.miqu.entity.Follow;
import com.miqu.entity.Message;
import com.miqu.entity.Notification;
import com.miqu.entity.Post;
import com.miqu.entity.PostImage;
import com.miqu.entity.PostLike;
import com.miqu.entity.Report;
import com.miqu.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体标注与真实表结构的一致性。
 *
 * <p>这个测试存在的理由是一个真实踩过的坑：{@code Report} 曾错误地继承了带
 * {@code @TableLogic} 的基类，而 {@code report} 表根本没有 {@code deleted} 列。
 * MyBatis-Plus 会给每条 SQL 自动追加 {@code WHERE deleted = 0}，
 * 于是第一次查询这张表就抛 {@code Unknown column 'deleted' in 'where clause'}。
 *
 * <p>这类错误**只要不查那张表就不会暴露**——实体写错了半年都可能没症状，
 * 直到某个新接口第一次用到它。靠人眼核对 schema 不可靠，交给测试。
 *
 * <p>两类问题都在覆盖范围内：
 * <ul>
 *   <li>实体有 {@code @TableLogic} 但表没有 {@code deleted} 列 → 查询直接报错</li>
 *   <li>表有 {@code deleted} 列但实体没有 {@code @TableLogic} → 已逻辑删除的数据会照常查出来</li>
 *   <li>实体字段在表里不存在 → SQL 报 Unknown column</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("实体与表结构一致性")
class EntitySchemaConsistencyTest {

    /** 新增实体时请一并登记，否则这个测试守护不到它。 */
    private static final List<Class<?>> ENTITIES = List.of(
            User.class, Post.class, Comment.class, Message.class, Notification.class,
            Conversation.class, Report.class, Follow.class, PostLike.class, PostImage.class,
            AdminOperationLog.class);

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("逻辑删除标注与表的 deleted 列必须一致")
    void logicDeleteAnnotationMatchesColumn() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (Class<?> entityClass : ENTITIES) {
                String table = tableNameOf(entityClass);
                boolean entityHasLogicDelete = findLogicDeleteField(entityClass) != null;
                boolean tableHasDeletedColumn = columnExists(connection, table, "deleted");

                assertThat(entityHasLogicDelete)
                        .as("""
                                %s 与表 `%s` 的逻辑删除标注不一致：
                                实体 @TableLogic=%s，表 deleted 列=%s。
                                实体有标注而表无该列 → 查询会抛 Unknown column 'deleted'；
                                表有该列而实体无标注 → 已删除的数据会被照常查出来。"""
                                .formatted(entityClass.getSimpleName(), table,
                                        entityHasLogicDelete, tableHasDeletedColumn))
                        .isEqualTo(tableHasDeletedColumn);
            }
        }
    }

    @Test
    @DisplayName("实体的每个字段都能在表中找到对应列")
    void everyFieldMapsToColumn() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            for (Class<?> entityClass : ENTITIES) {
                String table = tableNameOf(entityClass);

                for (Field field : instanceFields(entityClass)) {
                    TableField tableField = field.getAnnotation(TableField.class);
                    if (tableField != null && !tableField.exist()) {
                        continue;   // 显式声明为"不是数据库字段"
                    }

                    String column = camelToSnake(field.getName());
                    assertThat(columnExists(connection, table, column))
                            .as("%s.%s 对应的列 `%s`.`%s` 不存在",
                                    entityClass.getSimpleName(), field.getName(), table, column)
                            .isTrue();
                }
            }
        }
    }

    // ==================== 工具 ====================

    private String tableNameOf(Class<?> entityClass) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        assertThat(annotation)
                .as("%s 缺少 @TableName 注解", entityClass.getSimpleName())
                .isNotNull();
        return annotation.value();
    }

    /** 沿类继承链寻找 {@code @TableLogic} 字段（可能声明在基类上）。 */
    private Field findLogicDeleteField(Class<?> entityClass) {
        for (Class<?> current = entityClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(TableLogic.class)) {
                    return field;
                }
            }
        }
        return null;
    }

    /** 收集类继承链上的所有实例字段（含基类）。 */
    private List<Field> instanceFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = entityClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && !Modifier.isTransient(field.getModifiers())
                        && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        // MySQL 的元数据需要显式给出 schema（即库名），这里用连接当前的 catalog
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        // 某些驱动对大小写敏感，回退到小写再试一次
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null,
                table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
