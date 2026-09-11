package com.miqu.controller.admin;

import com.miqu.common.PageResult;
import com.miqu.common.Result;
import com.miqu.dto.query.AdminLogQuery;
import com.miqu.security.RequireAdmin;
import com.miqu.service.AdminService;
import com.miqu.vo.AdminOperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员操作日志查询。
 *
 * <p>日志是**只读**的：没有修改与删除接口。审计记录如果可以被后台随手删掉，
 * 那它就没有审计价值了。
 *
 * <p>日志内容严格限制：只写操作类型、目标与摘要，不记录密码、Token 等敏感信息
 * （见 {@code AdminLogServiceImpl}）。
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@RequireAdmin
@Tag(name = "管理后台 · 日志", description = "管理员操作审计")
public class AdminLogController {

    private final AdminService adminService;

    @GetMapping
    @Operation(summary = "操作日志列表",
            description = "按时间倒序，可按管理员 ID 与操作类型过滤。日志只读、不可修改删除。")
    public Result<PageResult<AdminOperationLogVO>> list(@Valid AdminLogQuery query) {
        return Result.ok(adminService.listLogs(query));
    }
}
