package com.miqu.controller;

import com.miqu.common.ErrorCode;
import com.miqu.common.Result;
import com.miqu.vo.HealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 健康检查。
 *
 * <p>免登录。用于确认服务已启动，也方便接口自动化测试在开跑前做一次可用性探测
 * （避免把"服务没起来"误判成"接口失败"）。
 *
 * <p><b>会实际探测数据库。</b> 只报告进程存活是不够的：
 * 数据库不可用时进程照样活着，但每个业务请求都会 500，
 * 这时健康检查还报 UP 就成了误导。所以这里真的去取一次连接。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "健康检查")
public class HealthController {

    /** 探测超时（秒）。取连接本身受连接池的 connection-timeout 约束。 */
    private static final int PROBE_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    @GetMapping("/health")
    @Operation(summary = "健康检查",
            description = """
                    整体健康时返回 `status=UP` 且业务码 200。

                    **会实际探测数据库**：数据库不可用时返回 `status=DOWN`、`database=DOWN`，
                    业务码 500。这样"进程活着但服务不可用"不会被误报成健康。
                    """)
    public Result<HealthVO> health() {
        boolean databaseUp = probeDatabase();
        if (databaseUp) {
            return Result.ok(new HealthVO("UP", "miqu", "UP"));
        }
        return Result.fail(ErrorCode.INTERNAL_ERROR, "数据库不可用，服务暂时无法提供业务功能",
                new HealthVO("DOWN", "miqu", "DOWN"));
    }

    private boolean probeDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(PROBE_TIMEOUT_SECONDS);
        } catch (Exception e) {
            // 只记录原因，不把连接串、账号等信息暴露给调用方
            log.error("健康检查：数据库连接失败 - {}", e.getMessage());
            return false;
        }
    }
}
