package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.Comment;

/** 评论 Mapper。删除为逻辑删除（无唯一约束，软删是安全的）。 */
public interface CommentMapper extends BaseMapper<Comment> {
}
