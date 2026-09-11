package com.miqu.service;

import com.miqu.common.PageResult;
import com.miqu.dto.query.AdminReportQuery;
import com.miqu.dto.request.CreateReportRequest;
import com.miqu.dto.request.HandleReportRequest;
import com.miqu.vo.AdminReportVO;
import com.miqu.vo.ReportVO;

public interface ReportService {

    /** 提交举报。不能举报自己的内容，同一目标不能重复举报。 */
    ReportVO create(Long userId, CreateReportRequest request);

    /** 管理后台：举报列表，带被举报对象的摘要。 */
    PageResult<AdminReportVO> listForAdmin(AdminReportQuery query);

    /**
     * 管理后台：处理举报。
     *
     * <p>可同时执行处置动作（删动态/删评论/禁用用户），
     * 与"处理结论"写在一次请求里，避免出现"举报已处理但内容还在"的中间态。
     */
    void handle(Long adminId, Long reportId, HandleReportRequest request);
}
