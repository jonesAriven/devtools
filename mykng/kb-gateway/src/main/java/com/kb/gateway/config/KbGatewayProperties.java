package com.kb.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关配置属性，绑定 application.yml 中 {@code kb.gateway.*}。
 * <p>
 * 类名刻意避开 Spring Cloud Gateway 内置的 {@code GatewayProperties}，以免 Bean 名称冲突。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb.gateway")
public class KbGatewayProperties {

    /** 统一上下文路径（与 KB_CONTEXT 环境变量一致） */
    private String contextPath = "/kb";

    /** JWT 配置 */
    private Jwt jwt = new Jwt();

    /** 鉴权白名单（Ant 风格路径） */
    private List<String> whitelist = new ArrayList<>();

    @Data
    public static class Jwt {
        /** 与 kb-auth 一致的 HMAC 密钥 */
        private String secret;
        /** 携带 token 的请求头名称 */
        private String header = "Authorization";
    }
}
