package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper。
 *
 * <p>计数类字段一律用**原子 UPDATE** 修改，禁止"查出来 +1 再写回"——
 * 后者在并发下会丢失更新。
 */
public interface UserMapper extends BaseMapper<User> {

    /** 关注数 +1。 */
    @Update("UPDATE `user` SET following_count = following_count + 1 WHERE id = #{userId} AND deleted = 0")
    int incrFollowingCount(@Param("userId") Long userId);

    /** 关注数 -1，用 GREATEST 兜底防止出现负数。 */
    @Update("UPDATE `user` SET following_count = GREATEST(following_count - 1, 0) WHERE id = #{userId} AND deleted = 0")
    int decrFollowingCount(@Param("userId") Long userId);

    /** 粉丝数 +1。 */
    @Update("UPDATE `user` SET follower_count = follower_count + 1 WHERE id = #{userId} AND deleted = 0")
    int incrFollowerCount(@Param("userId") Long userId);

    /** 粉丝数 -1，用 GREATEST 兜底防止出现负数。 */
    @Update("UPDATE `user` SET follower_count = GREATEST(follower_count - 1, 0) WHERE id = #{userId} AND deleted = 0")
    int decrFollowerCount(@Param("userId") Long userId);

    /** 动态数 +1。 */
    @Update("UPDATE `user` SET post_count = post_count + 1 WHERE id = #{userId} AND deleted = 0")
    int incrPostCount(@Param("userId") Long userId);

    /** 动态数 -1，用 GREATEST 兜底防止出现负数。 */
    @Update("UPDATE `user` SET post_count = GREATEST(post_count - 1, 0) WHERE id = #{userId} AND deleted = 0")
    int decrPostCount(@Param("userId") Long userId);
}
