package com.miqu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 项目自定义配置，对应 {@code application.yml} 中的 {@code miqu.*}。 */
@Data
@Component
@ConfigurationProperties(prefix = "miqu")
public class MiquProperties {

    private Jwt jwt = new Jwt();
    private Upload upload = new Upload();
    private Auth auth = new Auth();

    @Data
    public static class Jwt {
        /** HS256 密钥，**至少 32 字节**，启动时校验。生产环境必须用环境变量覆盖。 */
        private String secret;

        /** Token 有效期（秒），默认 7 天。 */
        private long expireSeconds = 7 * 24 * 60 * 60L;

        private String issuer = "miqu";
    }

    @Data
    public static class Upload {
        /** 本地存储根目录。 */
        private String baseDir = "./uploads";

        /** 对外暴露的 URL 前缀，需与 WebMvcConfig 的静态资源映射一致。 */
        private String urlPrefix = "/uploads";
    }

    @Data
    public static class Auth {
        /**
         * 免登录白名单。
         *
         * <p>格式：{@code METHODS:/ant/path}，METHODS 可省略表示任意方法。
         * 例：{@code GET:/api/posts}、{@code POST:/api/auth/login}、{@code /api/files/**}。
         *
         * <p>采用"白名单外一律要求登录"的失败关闭策略：新增接口若忘记配置，
         * 默认是受保护的，而不是裸奔。
         */
        private List<String> whiteList = new ArrayList<>();
    }
}
