package com.jones.activation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口级权限闸门配置属性，绑定 {@code application.yml} 中的 {@code marschat.authz.*}。
 *
 * <h3>背景（P0-3，2026-09-16）</h3>
 * activecode 改造前有「权限点、零闸门」：{@code menu-registry.yml} 已上报 6 个 api 权限点，
 * 中心 {@code sys_permission} 里也有 14 条 activecode 权限点，但应用侧**没有任何消费方**
 * （全仓 grep {@code @RequirePermission} / {@code PermissionChecker} 0 命中）。
 * {@link AuthInterceptor} 只判「有没有登录」，不判「有没有这个权限点」，等于「认证即全权」。
 *
 * <h3>范式来源（与 kb-gateway 同构，载体不同）</h3>
 * kb-gateway 的 {@code PermissionAuthzFilter} 是 WebFlux {@code GlobalFilter}；
 * activecode 是 Spring MVC（{@code HandlerInterceptor}）。本类与该 filter 的
 * {@code authz.*} 配置项同构（{@code enabled / fail-open / issuer / client-id /
 * cache-ttl-ms / rules}），仅 {@link Rule} 增加一个可选 {@code method} 字段（见下）。
 *
 * <h3>为什么 Rule 需要 method（与 kb-gateway 的一处有意差异）</h3>
 * kb-gateway 的规则是**前缀式**（如 {@code /api/doc/**}），路径本身即可唯一标识权限点。
 * activecode 的受管端点却共享同一段路径空间：{@code POST /activation/verify}（**保持匿名**）
 * 与 {@code POST /activation/generate}（需 {@code code:generate}）同为一层单段路径；
 * 仅凭路径通配（{@code /activation/*}）会把匿名 verify 一并拦下。故本类为规则增加
 * {@code method} 维度，做到「方法 + 路径」精确命中，避免误伤匿名端点。
 * {@code method} 留空 = 命中任意写方法（保持与 kb-gateway 完全同构的兼容形态）。
 */
@Component
@ConfigurationProperties(prefix = "marschat.authz")
public class AuthzProperties {

    /** 总开关。应急时置 {@code false} 即回到「只认证不鉴权」的旧行为（可回滚）。 */
    private boolean enabled = true;

    /**
     * 中心不可达 / 拉取失败时是否放行。
     * <p>🔴 默认 {@code false}（fail-closed）：接口 = 动作，判定不了就不放行动作；
     * 只影响命中 {@link #rules} 的写请求，读请求与未命中路径完全不受影响。
     */
    private boolean failOpen = false;

    /**
     * auth-center 服务端互调基址。
     * <p>activecode 部署在独立主机（内网 Debian 192.168.31.182），只挂自己的 compose 网络，
     * **不能**用容器名 {@code auth-center}，必须走宿主 LAN 地址（与
     * {@code marschat.auth-center.base} / {@code marschat.account.report.issuer} 同口径）。
     */
    private String issuer = "http://192.168.31.105:8085";

    /** 权限点归属的 client（activecode 的菜单与 api 点都注册在 marschat-activecode 下）。 */
    private String clientId = "marschat-activecode";

    /** 权限结果缓存时长（毫秒）——避免每个写请求都打 auth-center。 */
    private long cacheTtlMs = 60_000L;

    /** 规则表：命中者才需校验权限点。 */
    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** 单条闸门规则。 */
    public static class Rule {

        /** Ant 风格路径（**含应用前缀**，如 {@code /activecode/api/activation/generate}）。 */
        private String pattern;

        /**
         * 可选 HTTP 方法（大小写不敏感，如 {@code POST} / {@code DELETE} / {@code PUT}）。
         * <p>留空 = 命中任意写方法。
         */
        private String method;

        /** 需要的权限点短码（全码 = {@code <clientId>:api:<perm>}）。 */
        private String perm;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPerm() {
            return perm;
        }

        public void setPerm(String perm) {
            this.perm = perm;
        }
    }
}
