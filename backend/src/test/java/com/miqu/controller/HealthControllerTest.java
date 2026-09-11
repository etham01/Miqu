package com.miqu.controller;

import com.miqu.common.Result;
import com.miqu.vo.HealthVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 健康检查控制器。
 *
 * <p>这个类守护的是一个真实踩过的坑：数据库不可用时，
 * 健康检查**必须报告 DOWN**。
 *
 * <p>最初的实现只返回 {@code status=UP}，因为进程确实活着——
 * 但那时每个业务请求都会 500，健康检查却报"健康"，把排查方向完全带偏了。
 * 调用方（含 pytest 的后端探活）会因此把"数据库挂了"误判成"接口有问题"。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("健康检查")
class HealthControllerTest {

    @Mock
    private DataSource dataSource;

    @Test
    @DisplayName("数据库可用时报告 UP，业务码 200")
    void reportsUpWhenDatabaseAvailable() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        Result<HealthVO> result = new HealthController(dataSource).health();

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data().status()).isEqualTo("UP");
        assertThat(result.data().database()).isEqualTo("UP");
        assertThat(result.data().application()).isEqualTo("miqu");
    }

    @Test
    @DisplayName("取连接失败时报告 DOWN，业务码 500")
    void reportsDownWhenConnectionFails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Access denied for user 'root'@'localhost'"));

        Result<HealthVO> result = new HealthController(dataSource).health();

        assertThat(result.code()).isEqualTo(500);
        assertThat(result.data().status()).isEqualTo("DOWN");
        assertThat(result.data().database()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("连接拿得到但已失效时同样报告 DOWN")
    void reportsDownWhenConnectionIsInvalid() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        Result<HealthVO> result = new HealthController(dataSource).health();

        assertThat(result.code()).isEqualTo(500);
        assertThat(result.data().database()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("失败响应不泄漏连接串、账号等内部信息")
    void failureResponseDoesNotLeakInternals() throws Exception {
        when(dataSource.getConnection())
                .thenThrow(new SQLException("Access denied for user 'root'@'localhost' (using password: NO)"));

        Result<HealthVO> result = new HealthController(dataSource).health();

        assertThat(result.message()).doesNotContain("root");
        assertThat(result.message()).doesNotContain("password");
        assertThat(result.message()).doesNotContain("jdbc:");
    }
}
