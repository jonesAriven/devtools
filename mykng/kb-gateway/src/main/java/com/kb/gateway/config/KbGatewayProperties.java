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

    /**
     * 接口级权限闸门（T5，2026-09-15）。
     *
     * <p>背景：kb-web 的写接口此前**只有认证没有鉴权** —— 网关只验签，谁有 token 谁就能写。
     * 本轮在网关侧补上「中心权限点」闸门：命中 {@link Authz#rules} 的写请求必须持有
     * {@code marschat-kbweb:api:<perm>} 权限点。
     *
     * <p>为什么放在网关而不是各业务服务：kb-file / kb-knowledge / kb-intelligence 只依赖
     * common-core（无 auth-core、无 Servlet Security 链），逐个接入成本与回归面都远大于
     * 在网关**一处收口**。网关已持有已验签的用户身份与原始 token，是天然的鉴权点。
     */
    private Authz authz = new Authz();

    @Data
    public static class Jwt {
        /** 与 auth-center 一致的 HMAC 密钥 */
        private String secret;
        /** 携带 token 的请求头名称 */
        private String header = "Authorization";
    }

    /** 接口级权限闸门配置（{@code kb.gateway.authz.*}）。 */
    @Data
    public static class Authz {
        /** 总开关（关掉即回到「只认证不鉴权」的旧行为）。 */
        private boolean enabled = true;

        /**
         * 中心不可达 / 拉取失败时是否放行。
         * <p>🔴 默认 {@code false}（fail-closed）：接口 = 动作，判定不了就不放行动作；
         * 只影响命中 {@link #rules} 的写请求，读请求与未命中路径完全不受影响。
         */
        private boolean failOpen = false;

        /** auth-center 服务端互调基址（走容器网络，禁走公网域名）。 */
        private String issuer = "http://auth-center:8085";

        /** 权限点归属的 client（kb-web 的菜单与权限点都注册在 marschat-kbweb 下）。 */
        private String clientId = "marschat-kbweb";

        /** 权限结果缓存时长（毫秒）——避免每个写请求都打 auth-center。 */
        private long cacheTtlMs = 60_000;

        /** 路径 → 权限点规则（Ant 风格，**相对于 contextPath**，如 {@code /api/doc/**}）。 */
        private List<Rule> rules = new ArrayList<>();
    }

    /** 单条闸门规则。 */
    @Data
    public static class Rule {
        /** Ant 风格路径（相对 contextPath）。 */
        private String pattern;
        /** 需要的权限点短码（全码 = {@code <clientId>:api:<perm>}）。 */
        private String perm;
    }
}
