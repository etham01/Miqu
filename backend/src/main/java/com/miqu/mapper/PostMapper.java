package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.miqu.entity.Post;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 动态 Mapper。
 *
 * <p>计数类字段一律用**原子 UPDATE**（{@code x = x + 1}）在数据库侧完成，
 * 禁止"查出来 +1 再写回"——后者在并发下会丢失更新。
 * 减法统一用 {@code GREATEST(x-1, 0)} 兜底，避免出现负数。
 */
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE `post` SET like_count = like_count + 1 WHERE id = #{postId} AND deleted = 0")
    int incrLikeCount(@Param("postId") Long postId);

    @Update("UPDATE `post` SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{postId} AND deleted = 0")
    int decrLikeCount(@Param("postId") Long postId);

    @Update("UPDATE `post` SET comment_count = comment_count + 1 WHERE id = #{postId} AND deleted = 0")
    int incrCommentCount(@Param("postId") Long postId);

    @Update("UPDATE `post` SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{postId} AND deleted = 0")
    int decrCommentCount(@Param("postId") Long postId);

    /**
     * 关注动态流。
     *
     * <p>用 JOIN 而不是 {@code user_id IN (SELECT ...)}：后者在关注数变多时会退化成
     * 大 IN 列表或相关子查询。JOIN 走 {@code uk_follower_following} 与
     * {@code idx_time} 索引，代价稳定。
     *
     * <p>注意：自定义 SQL **不会**被 MyBatis-Plus 自动追加逻辑删除条件，
     * 因此这里必须手写 {@code p.deleted = 0}，漏了就会把已删动态查出来。
     */
    @Select("""
            SELECT p.*
            FROM `post` p
            INNER JOIN `follow` f ON f.following_id = p.user_id
            WHERE f.follower_id = #{followerId}
              AND p.deleted = 0
            ORDER BY p.create_time DESC, p.id DESC
            """)
    IPage<Post> selectFollowingFeed(IPage<Post> page, @Param("followerId") Long followerId);
}
