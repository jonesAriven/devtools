package com.kb.ops.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 统一认证中心 {@code /admin/**} 的 BFF 透传代理（kb-ops 用户 / 角色 / 账号映射管理页数据源）。
 *
 * <h3>为什么需要它</h3>
 * kb-ops 是纯 SSO 应用（无本地 login 端点），浏览器侧只持有 auth-center 签发的 OIDC
 * access_token；此前前端直连 {@code auth.marschat.online/admin/**}（跨域）。本代理把请求改为
 * 同源（{@code /kb-ops/admin/**}，经 mykng nginx {@code /ops-api/} 进入），透传调用者本人的
 * Authorization 头给 auth-center，与其它 5 个应用同口径（Phase 12 · 3.2-A）。
 *
 * <h3>凭据从哪来</h3>
 * 调用者的 OIDC access_token 就在本次请求的 {@code Authorization} 头里（kb-ops 的
 * {@code JwtAuthenticationFilter} 已据此验签）。本代理<b>直接透传该头</b>，绝不服务账号兜底；
 * 拿不到头 → 401 让前端走正常重授权（与 activecode / infra-monitor 的 BFF 同一取舍）。
 *
 * <h3>安全边界（务必保持）</h3>
 * <ul>
 *   <li><b>绝不使用服务账号兜底</b>：只转发调用方自己的 token。历史上正是「无头时回退服务账号」
 *       造成提权，会把管理员能力下发给普通用户。</li>
 *   <li>拿不到中心凭据（无 Authorization 头）→ 直接 {@code 401}，不转发、不伪造。</li>
 *   <li><b>白名单（默认拒绝，Phase 12 §3.2-A）</b>：仅放行本应用管理页实际所需端点，其余一律 404。
 *       收窄前 {@code /admin/**} 全透传，中心每新增一个管理端点，本应用任意登录用户持自己的
 *       中心 token 即可触达（含 {@code DELETE /admin/users/{id}}、{@code PUT /admin/users/{id}/password}）。
 *       现改为白名单，新增端点须显式登记（见 {@link #isAllowed}）。</li>
 *   <li><b>角色判定仍不在本代理做</b>：中心返回什么状态就原样透传（非管理员 → 中心 403，
 *       token 过期 → 中心 401）。应用侧不复制一份平台角色判定，避免两处真源不一致。</li>
 * </ul>
 */
@RestController
public class AdminProxyController {

    private static final Logger log = LoggerFactory.getLogger(AdminProxyController.class);

    /** 本应用在统一认证中心的 client_id（白名单作用域绑定；中心侧 path 化端点用同一值）。 */
    private static final String CLIENT_ID = "marschat-kbops";

    /** 既有「本系统角色」端点：{@code /admin/users/{id}/client-roles}。 */
    private static final Pattern USER_CLIENT_ROLES = Pattern.compile("/admin/users/[^/]+/client-roles");
    /** 既有「菜单减法」端点：{@code /admin/users/{id}/menu-overrides}。 */
    private static final Pattern USER_MENU_OVERRIDES = Pattern.compile("/admin/users/[^/]+/menu-overrides");

    /** 中心内网基址（与 kb-ops 的 OIDC jwks-uri / 菜单上报同口径：compose 内网直连 auth-center）。 */
    @Value("${marschat.auth-center.base:http://auth-center:8085}")
    private String authCenterBase;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 代理中心 {@code /admin/**}。
     *
     * <p>请求 {@code /kb-ops/admin/users?client=marschat-kbops}
     * → 中心 {@code /admin/users?client=marschat-kbops}。</p>
     */
    @RequestMapping("/admin/**")
    public ResponseEntity<String> proxyAdmin(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        String method = request.getMethod();
        String pathOnly = extractPathOnly(request);
        // 白名单（默认拒绝）：非白名单路径无论是否有凭据一律 404，不泄漏、不转发。
        if (!isAllowed(method, pathOnly, request.getQueryString())) {
            log.warn("管理代理拒绝非白名单路径（{} {}）", method, pathOnly);
            return json(404, "{\"code\":404,\"message\":\"接口不存在\",\"data\":null}");
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            // fail-closed：绝不回退服务账号，交由前端触发重授权
            return json(401, "{\"code\":401,\"message\":\"无统一认证中心凭据，请重新登录\",\"data\":null}");
        }
        String path = extractCenterPath(request);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(authCenterBase.replaceAll("/+$", "") + path))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10));
            switch (method.toUpperCase()) {
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(
                        body == null ? "" : body, StandardCharsets.UTF_8));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(
                        body == null ? "" : body, StandardCharsets.UTF_8));
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }
            HttpResponse<String> response = HTTP.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 状态码与响应体原样透传：前端按中心 Result 信封（code/message/data）解析
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (Exception e) {
            log.warn("认证中心管理代理失败（{} {}）: {}", method, path, e.getMessage());
            return json(502, "{\"code\":502,\"message\":\"认证中心不可达，请稍后重试\",\"data\":null}");
        }
    }

    /**
     * 取本次请求对应的中心路径并拼回查询串：
     * {@code /kb-ops/admin/users?x=1} → {@code /admin/users?x=1}。
     */
    private String extractCenterPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/admin");
        String path = idx >= 0 ? uri.substring(idx) : "/admin";
        String qs = request.getQueryString();
        return (qs == null || qs.isBlank()) ? path : path + "?" + qs;
    }

    /**
     * 从请求 URI 截出中心路径（不含查询串，去尾部斜杠），用于白名单匹配：
     * {@code /kb-ops/admin/users?x=1} → {@code /admin/users}。
     */
    private String extractPathOnly(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/admin");
        String path = idx >= 0 ? uri.substring(idx) : "/admin";
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 管理代理白名单（默认拒绝，Phase 12 §3.2-A）。
     *
     * <p>只放行本应用「用户管理 / 角色绑定 / 菜单减法 / 账号映射」页实际所需的中心端点；
     * 任何未列出的路径一律拒绝（调用方返回 404）：
     * <ul>
     *   <li>{@code /admin/clients/marschat-kbops/members**}、{@code /admin/clients/marschat-kbops/users/**}
     *       —— 中心侧新增的 path 化成员 / 应用级端点（client 已钉进 path）。</li>
     *   <li>{@code GET /admin/users} —— 成员列表 + 「添加已有用户」候选池（只读）；
     *       带 {@code client=} 参数时必须等于本应用 {@link #CLIENT_ID}，否则拒绝（防改 client 越界）。</li>
     *   <li>{@code GET|PUT /admin/users/{id}/client-roles?client=<本应用>} —— 本系统角色绑定。</li>
     *   <li>{@code GET|PUT /admin/users/{id}/menu-overrides?client=<本应用>} —— 菜单减法。</li>
     *   <li>{@code GET /admin/roles} —— 角色定义（只读）。</li>
     *   <li>{@code /admin/mappings*} —— 账号映射（平台管理员专属；前端页签已对应用管理员隐藏，
     *       中心侧 @PreAuthorize 挡非管理员兜底安全）。</li>
     * </ul>
     *
     * <p><b>被排除（一律 404）</b>：{@code POST /admin/users}、{@code PUT /admin/users/{id}}、
     * {@code DELETE /admin/users/{id}}、{@code PUT /admin/users/{id}/password}、
     * 以及其它 client 的任何路径。Phase 12 R2 后应用台已无新建 / 编辑入口，故收回透传。
     */
    private boolean isAllowed(String method, String path, String query) {
        String m = method == null ? "" : method.toUpperCase();

        // ① 中心侧新增的 path 化端点：client 已钉进 path，只放行本应用。
        if (path.equals("/admin/clients/" + CLIENT_ID)
                || path.startsWith("/admin/clients/" + CLIENT_ID + "/")) {
            return true;
        }

        // ② 成员列表 / 全平台用户池：仅 GET 读；带 client= 时必须等于本应用。
        if (path.equals("/admin/users")) {
            return "GET".equals(m) && clientScopeOk(query);
        }

        // ③ 既有「本系统角色」端点：GET 读 / PUT 写，且 client 必须等于本应用。
        if (USER_CLIENT_ROLES.matcher(path).matches()) {
            return ("GET".equals(m) || "PUT".equals(m)) && clientScopeOk(query);
        }

        // ④ 既有「菜单减法」端点：GET 读 / PUT 写，且 client 必须等于本应用。
        if (USER_MENU_OVERRIDES.matcher(path).matches()) {
            return ("GET".equals(m) || "PUT".equals(m)) && clientScopeOk(query);
        }

        // ⑤ 角色定义只读：GET /admin/roles。
        if (path.equals("/admin/roles") && "GET".equals(m)) {
            return true;
        }

        // ⑥ 账号映射（平台管理员专属；中心挡非管理员兜底）：GET / PUT 均放行。
        if (path.equals("/admin/mappings") || path.startsWith("/admin/mappings/")) {
            return true;
        }

        // 其余一律拒绝。
        return false;
    }

    /**
     * 校验 {@code client} 作用域参数：缺省视为放行；传了则必须等于本应用 {@link #CLIENT_ID}。
     * 用于防止把 {@code client=} 改成其它应用，借本代理越界管理别的 client。
     */
    private boolean clientScopeOk(String query) {
        String client = queryParam(query, "client");
        return client == null || client.isBlank() || CLIENT_ID.equals(client);
    }

    /** 从查询串中取指定参数并 URL 解码；不存在返回 {@code null}。 */
    private String queryParam(String query, String key) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if (key.equals(k)) {
                String v = eq >= 0 ? pair.substring(eq + 1) : "";
                return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
