package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.PostLike;

/**
 * 点赞 Mapper。
 *
 * <p><b>取消点赞必须是物理 DELETE。</b> 唯一键 {@code uk_post_user} 必须在取消后立即释放，
 * 否则用户"取消点赞后再点赞"会被唯一键挡住，需求里这条规则直接失效。
 */
public interface PostLikeMapper extends BaseMapper<PostLike> {
}
