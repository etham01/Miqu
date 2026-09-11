package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.Follow;

/**
 * 关注关系 Mapper。
 *
 * <p>列表查询走"分页查关系 → 批量查用户 → Map 回填"的方式，
 * 不需要自定义 SQL；这样也便于复用统一的 N+1 规避套路。
 */
public interface FollowMapper extends BaseMapper<Follow> {
}
