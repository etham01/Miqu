package com.miqu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miqu.entity.Report;

/**
 * 举报 Mapper。
 *
 * <p>注意 {@code target_id} 是**多态外键**：它指向 user / post / comment 三者之一，
 * 因此无法建物理外键，引用完整性由 Service 层按 {@code target_type} 分支校验。
 */
public interface ReportMapper extends BaseMapper<Report> {
}
