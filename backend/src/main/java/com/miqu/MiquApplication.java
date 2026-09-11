package com.miqu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Miqu「觅取」社交系统 —— 应用入口。
 */
@Slf4j
@SpringBootApplication
public class MiquApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(MiquApplication.class, args);

        // 必须在启动阶段就验证数据库可连接，不能等到第一个请求。
        // 见 verifyDatabase 的注释。
        verifyDatabase(ctx);
        printStartupBanner(ctx);
    }

    /**
     * 启动时验证数据库连接，失败则打印可操作的提示并退出。
     *
     * <p><b>为什么需要这一步：</b> Hikari 连接池是**懒初始化**的——应用启动时不会去连数据库，
     * 直到第一个请求才建立连接。因此数据库口令配错时，应用会"启动成功"，
     * 然后每个请求都返回 500「系统异常，请稍后重试」，而日志里才有真正的原因。
     *
     * <p>对使用者来说这是最糟的失败模式：服务看起来起来了，前端却什么都做不了，
     * 报错信息和根因完全对不上。这里把它前移到启动阶段，并直接说清楚怎么修。
     */
    private static void verifyDatabase(ConfigurableApplicationContext ctx) {
        Environment env = ctx.getEnvironment();
        String url = env.getProperty("spring.datasource.url", "(未配置)");
        String username = env.getProperty("spring.datasource.username", "(未配置)");

        try (Connection connection = ctx.getBean(DataSource.class).getConnection()) {
            log.info("数据库连接正常：{}", connection.getMetaData().getURL());
        } catch (Exception e) {
            log.error("""

                    ============================================================
                     启动失败：无法连接数据库
                    ------------------------------------------------------------
                     当前配置
                       地址    {}
                       用户名  {}
                     失败原因
                       {}
                    ------------------------------------------------------------
                     请依次检查

                     1) MySQL 是否已启动

                     2) 是否提供了数据库口令。
                        项目**不把密码写进配置文件**，需通过环境变量传入：

                          Git Bash    export DB_USERNAME=root DB_PASSWORD=你的密码
                          PowerShell  $env:DB_USERNAME="root"; $env:DB_PASSWORD="你的密码"
                          CMD         set DB_USERNAME=root && set DB_PASSWORD=你的密码

                        然后重新启动后端。

                     3) 数据库是否已初始化：

                          mysql -u root -p < database/schema.sql
                          mysql -u root -p < database/data.sql

                     其他连接参数（换机器时可能需要）：
                       DB_HOST（默认 localhost）· DB_PORT（默认 3306）· DB_NAME（默认 miqu）
                    ============================================================
                    """, url, username, rootCauseMessage(e));

            // 用 SpringApplication.exit 走正常关闭流程，确保连接池等资源被释放
            System.exit(SpringApplication.exit(ctx, () -> 1));
        }
    }

    /** 取最内层异常的信息：外层包装的文案往往是"Failed to obtain JDBC Connection"，没有价值。 */
    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private static void printStartupBanner(ConfigurableApplicationContext ctx) {
        Environment env = ctx.getEnvironment();
        String port = env.getProperty("server.port", "8081");
        String context = env.getProperty("server.servlet.context-path", "");
        String base = "http://localhost:" + port + context;

        log.info("""

                        ----------------------------------------------------------
                          Miqu 觅取社交系统启动完成
                          接口文档: {}/swagger-ui.html
                          健康检查: {}/api/health
                        ----------------------------------------------------------""",
                base, base);
    }
}
