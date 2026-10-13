package com.jones.activation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🔓 放开登录校验的配置化白名单（2026-10-14）。
 *
 * <p><b>背景</b>：匿名清单此前硬编码在两处——{@code WebMvcConfig#excludePathPatterns}
 * （路径级）与 {@code AuthInterceptor} 的 {@code ANONYMOUS_ENDPOINTS}（方法级），
 * 每次收放匿名权都要改代码 + 走流水线发版。现统一收敛到本配置：
 *
 * <pre>
 * marschat:
 *   auth:
 *     anonymous:
 *       paths:                 # 路径级：整条路径不拦截
 *         - /activecode/index.html
 *       endpoints:             # 方法级：路径仍拦截，仅列出的方法放行
 *         - path: /activecode/api/activation/generate
 *           methods: [POST]
 * </pre>
 *
 * <p><b>覆盖语义</b>：yml 写了 {@code paths} / {@code endpoints} 即<b>整体替换</b>
 * 对应清单（配置即真相，收回匿名权 = 从 yml 删条目重启）；未写的字段沿用代码默认值
 * （与 2026-10-14 线上清单一致）。
 *
 * <p>🔒 <b>登录保底集不可清空</b>：{@link #FALLBACK_PATHS} 是「还没有会话时也必须可达」
 * 的登录基础设施（账号密码登录 / SSO / 邮箱验证码 / 自助改密 / session 探测 / favicon），
 * 由代码合并进生效清单，配置无法移除——防止误配把登录链路打断导致没人能登录。
 * 业务性放开请配置 {@link #paths} / {@link #endpoints}，勿动此集。
 */
@Component
@ConfigurationProperties(prefix = "marschat.auth.anonymous")
public class AnonymousAuthProperties {

    /**
     * 🔒 登录保底集：代码内合并，配置不可清空。
     * 与改造前 {@code WebMvcConfig#excludePathPatterns} 中的登录基础设施段一致。
     */
    private static final Set<String> FALLBACK_PATHS = Set.of(
            "/activecode/login.html",
            "/activecode/sso-callback.html",
            "/activecode/api/auth/login",
            "/activecode/api/auth/sso-login",
            "/activecode/api/auth/session",
            "/activecode/api/auth/mail-login",
            "/activecode/api/auth/mail-login/send-code",
            "/activecode/api/auth/forgot-password",
            "/activecode/api/auth/reset-password",
            "/favicon.ico"
    );

    /**
     * 路径级匿名清单（整条路径不拦截）。默认值 = 改造前 excludePathPatterns 的业务部分。
     * yml 配置后整体替换。
     */
    private List<String> paths = new ArrayList<>(List.of(
            "/activecode/index.html",
            "/activecode/downloads.html",
            "/activecode/api/download/**",
            "/activecode/api/activation/verify"
    ));

    /**
     * 方法级匿名清单（路径仍拦截，仅列出的 HTTP 方法放行）。默认值 = 改造前
     * {@code AuthInterceptor#ANONYMOUS_ENDPOINTS}。yml 配置后整体替换。
     */
    private List<Endpoint> endpoints = new ArrayList<>(List.of(
            new Endpoint("/activecode/api/activation/generate", Set.of("POST")),
            new Endpoint("/activecode/api/activation/config/default-expire", Set.of("GET"))
    ));

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * 生效的路径级匿名清单 = 配置 paths ∪ 登录保底集（保底不可清空）。
     * 供 {@code WebMvcConfig} 的 excludePathPatterns 使用（Ant 风格通配）。
     */
    public Set<String> effectivePaths() {
        Set<String> all = new LinkedHashSet<>(FALLBACK_PATHS);
        if (paths != null) {
            all.addAll(paths);
        }
        return all;
    }

    /**
     * 生效的方法级匿名清单：path → methods（方法统一大写）。
     * 供 {@code AuthInterceptor#isAnonymousEndpoint} 使用（精确路径匹配，无通配）。
     */
    public Map<String, Set<String>> endpointMap() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        if (endpoints != null) {
            for (Endpoint e : endpoints) {
                if (e.getPath() == null || e.getMethods() == null || e.getMethods().isEmpty()) {
                    continue;
                }
                Set<String> upper = new LinkedHashSet<>();
                for (String m : e.getMethods()) {
                    if (m != null && !m.isBlank()) {
                        upper.add(m.trim().toUpperCase());
                    }
                }
                map.put(e.getPath(), upper);
            }
        }
        return map;
    }

    /** 方法级白名单条目。 */
    public static class Endpoint {

        /** 精确请求路径（与 request.getRequestURI() 全等匹配，不支持通配）。 */
        private String path;

        /** 放行的 HTTP 方法（GET/POST/...，大小写不敏感）。 */
        private Set<String> methods;

        public Endpoint() {
        }

        public Endpoint(String path, Set<String> methods) {
            this.path = path;
            this.methods = methods;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Set<String> getMethods() {
            return methods;
        }

        public void setMethods(Set<String> methods) {
            this.methods = methods;
        }
    }
}
