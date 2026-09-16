package com.kb.infra.controller;

import com.kb.infra.service.CenterSessionStore;
import com.kb.infra.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
import java.security.Principal;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 统一认证中心 {@code /admin/**} 的 BFF 前缀透传代理。
 *
 * <h3>为什么需要它（2026-09-15 真浏览器实测的缺陷）</h3>
 * 账密登录统一到认证中心后（Phase 11），本应用会话持有的是**本应用自签 HS256 token**，
 * 它调不动中心 {@code /admin/**}。而「用户管理」页需要以中心身份访问该接口 ——
 * 改造前前端直连 {@code https://auth.marschat.online/admin/users}，账密会话下必然 401，
 * 前端 401 拦截器随即触发静默重授权，**点一下「用户管理」就跳 IdP 登录页**。
 * 本代理把该请求改为同源 → 本服务以**用户本人持有的中心 token** 转发，与其它 5 个应用同口径。
 *
 * <h3>凭据从哪来（两种会话各自成立）</h3>
 * <ul>
 *   <li><b>SSO 会话</b>：浏览器本地存的就是中心 OIDC access token，随 {@code Authorization}
 *       一起到达，本地验签（RS256）通过 → 直接用它转发中心。</li>
 *   <li><b>账密 / 邮箱码会话</b>：{@code Authorization} 是本应用自签 token（中心不认），
 *       中心 accessToken 由 {@link CenterSessionStore} 在登录时按用户名暂存，此处取用。</li>
 * </ul>
 *
 * <h3>安全边界（务必保持）</h3>
 * <ul>
 *   <li><b>绝不使用服务账号兜底</b>：只转发调用方自己的 token。历史上正是「无会话时回退服务账号」
 *       造成提权（服务账号是 admin，会把管理员能力下发给普通用户）。</li>
 *   <li>两种来源都拿不到中心凭据 → 直接 {@code 401}，不转发、不伪造，让前端走正常重授权。</li>
 *   <li>入口仍受本应用 SecurityFilterChain 保护（{@code anyRequest().authenticated()}）：
 *       未携带有效本应用会话的请求根本进不到本方法。</li>
 *   <li>只代理 {@code /api/admin/**}；中心的 {@code /internal/**}（应用上报内网通道，带 secret）
 *       与 {@code /auth/**} 一律不在透传范围内，不会被本代理暴露。</li>
 *   <li><b>白名单（默认拒绝，Phase 12 §1.7 Step 3）</b>：不再前缀全透传。仅放行本应用
 *       「本系统用户」页所需的中心端点，其余一律 {@code 404}。收窄前 {@code /admin/**} 全透传，
 *       中心每新增一个管理端点，本应用**任意登录用户**持自己的中心 token 即可触达（含
 *       {@code DELETE /admin/users/{id}}、{@code PUT /admin/users/{id}/password}）—— 边界只会越放越宽。
 *       现改为白名单，新增端点须显式登记（见 {@link #isAllowed}）。</li>
 *   <li>角色判定不在本代理做：中心返回什么状态就原样透传（非管理员 → 中心 403，token 过期 → 中心 401）。</li>
 * </ul>
 *
 * <h3>白名单为什么不用「前缀透传」</h3>
 * infra-monitor 的「本系统用户」页只用到成员列表 / 本系统角色绑定 / 身份编辑这几类端点，
 * 因此按 {@code 方法 + 路径} 白名单精确放行即可；{@code client=marschat-inframon} 这类
 * 作用域参数在放行时一并校验（防止改 {@code client=} 越界到其它应用）。
 * 请求方法、查询串（keyword/page/size）、请求体仍原样转发，不会丢参数（portal 曾因此丢分页与应用作用域过滤参数）。
 */
@Slf4j
@RestController
public class AdminProxyController {

    /** 本应用在统一认证中心的 client_id（白名单作用域绑定；中心侧 path 化端点用同一值）。 */
    private static final String CLIENT_ID = "marschat-inframon";

    /** 单用户路径：{@code /admin/users/{id}}（仅放行 PUT 编辑；删/改密被白名单排除）。 */
    private static final Pattern USER_ONE = Pattern.compile("/admin/users/[^/]+");
    /** 既有「本系统角色」端点：{@code /admin/users/{id}/client-roles}。 */
    private static final Pattern USER_CLIENT_ROLES = Pattern.compile("/admin/users/[^/]+/client-roles");
    /** 既有「菜单减法」端点：{@code /admin/users/{id}/menu-overrides}。 */
    private static final Pattern USER_MENU_OVERRIDES = Pattern.compile("/admin/users/[^/]+/menu-overrides");

    private final JwtUtil jwtUtil;
    private final CenterSessionStore centerSessions;

    /** 中心内网基址（本应用 host 网络 → 127.0.0.1:8085；公网域名亦可用） */
    @Value("${auth-center.base:http://127.0.0.1:8085}")
    private String authCenterBase;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AdminProxyController(JwtUtil jwtUtil, CenterSessionStore centerSessions) {
        this.jwtUtil = jwtUtil;
        this.centerSessions = centerSessions;
    }

    /**
     * 代理中心 {@code /admin/**}。
     *
     * <p>请求 {@code /infra/api/admin/users?client=marschat-inframon}
     * → 中心 {@code /admin/users?client=marschat-inframon}。
     */
    @RequestMapping("/api/admin/**")
    public ResponseEntity<String> proxyAdmin(HttpServletRequest request,
                                             @RequestBody(required = false) String body) {
        String method = request.getMethod();
        String pathOnly = extractPathOnly(request);
        // 🔒 白名单（默认拒绝）：只放行本应用「本系统用户」页所需的中心端点，其余一律 404。
        // 放在取 token 之前 —— 非白名单路径无论会话如何一律 404，不泄漏、不转发。
        if (!isAllowed(method, pathOnly, request.getQueryString())) {
            log.warn("管理代理拒绝非白名单路径（{} {}）", method, pathOnly);
            return json(404, "{\"code\":404,\"message\":\"接口不存在\",\"data\":null}");
        }
        String centerToken = resolveCenterToken(request);
        if (centerToken == null) {
            // fail-closed：绝不回退服务账号，交由前端触发重授权
            return json(401, "{\"code\":401,\"message\":\"无统一认证中心会话，请重新登录\",\"data\":null}");
        }
        String path = extractCenterPath(request);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(authCenterBase + path))
                    .header("Authorization", "Bearer " + centerToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10));
            switch (request.getMethod().toUpperCase()) {
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }
            HttpResponse<String> response = HTTP.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 状态码与响应体原样透传：组件按中心 Result 信封（code/message/data）解析
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (Exception e) {
            log.warn("认证中心管理代理失败（{} {}）: {}", request.getMethod(), path, e.getMessage());
            return json(502, "{\"code\":502,\"message\":\"认证中心不可达，请稍后重试\",\"data\":null}");
        }
    }

    /**
     * 解析本次请求可用的**中心** accessToken；无则返回 {@code null}。
     *
     * <p>顺序：先用调用方自己的 token（仅当它不是本应用自签的 —— 即 SSO 会话带来的中心 OIDC token），
     * 否则回落到该用户在 {@link CenterSessionStore} 里暂存的账密登录换来的中心 token。
     */
    private String resolveCenterToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String presented = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;

        // SSO 会话：Authorization 里就是中心 OIDC token（本应用验不过自签密钥 → 说明非本应用签发）
        if (presented != null && !presented.isEmpty() && jwtUtil.parseLocalUsername(presented) == null) {
            return presented;
        }
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : centerSessions.getAccessToken(principal.getName());
    }

    /**
     * 从请求 URI 截出中心路径并拼回查询串：{@code /infra/api/admin/users?x=1} → {@code /admin/users?x=1}。
     *
     * <p>用 {@code indexOf("/api/admin")} 而非按 context-path 长度裁剪：后者在反代改写
     * （nginx 把 {@code /infra/api/} 映射到 {@code /infra/}）时会切错位置。
     */
    private String extractCenterPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/api/admin");
        String path = idx >= 0 ? uri.substring(idx + "/api".length()) : "/admin";
        String qs = request.getQueryString();
        return (qs == null || qs.isBlank()) ? path : path + "?" + qs;
    }

    /**
     * 从请求 URI 截出中心路径（**不含查询串**，去尾部斜杠），用于白名单匹配：
     * {@code /infra/api/admin/users?x=1} → {@code /admin/users}。
     */
    private String extractPathOnly(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/api/admin");
        String path = idx >= 0 ? uri.substring(idx + "/api".length()) : "/admin";
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 管理代理**白名单**（默认拒绝，Phase 12 §1.7 Step 3）。
     *
     * <p>只放行本应用「本系统用户」页实际所需的中心端点；任何未列出的路径一律拒绝（调用方返回 404）：
     * <ul>
     *   <li>{@code /admin/clients/marschat-inframon/members**}、{@code /admin/clients/marschat-inframon/users/**}
     *       —— 中心侧新增的 path 化成员/应用级端点（client 已钉进 path）。</li>
     *   <li>{@code GET|POST /admin/users} —— 成员列表 + 「添加已有用户」的全平台用户池（读）；
     *       若带 {@code client=} 参数，必须等于本应用 {@link #CLIENT_ID}，否则拒绝（防改 client 越界）。</li>
     *   <li>{@code PUT /admin/users/{id}} —— 编辑统一身份的资料字段（共享面板 app 作用域仍渲染「编辑」按钮）。</li>
     *   <li>{@code GET|PUT /admin/users/{id}/client-roles?client=<本应用>} —— 本系统角色绑定。</li>
     *   <li>{@code GET|PUT /admin/users/{id}/menu-overrides?client=<本应用>} —— 菜单减法。</li>
     *   <li>{@code GET /admin/roles} —— 角色定义（只读；用于渲染「本系统角色」勾选）。</li>
     * </ul>
     *
     * <p><b>被排除（一律 404）</b>：{@code DELETE /admin/users/{id}}、{@code PUT /admin/users/{id}/password}、
     * {@code /admin/mappings/**}、{@code /admin/authorization-matrix}、{@code /admin/authz/**}、
     * {@code /admin/roles} 的写方法、以及**其它 client** 的任何路径。
     *
     * @param method HTTP 方法
     * @param path   中心路径（不含查询串）
     * @param query  原始查询串（用于校验 {@code client} 作用域参数）
     * @return 放行返回 {@code true}，否则 {@code false}
     */
    private boolean isAllowed(String method, String path, String query) {
        String m = method == null ? "" : method.toUpperCase();

        // ① 中心侧新增的 path 化端点：client 已钉进 path，只放行本应用。
        if (path.equals("/admin/clients/" + CLIENT_ID)
                || path.startsWith("/admin/clients/" + CLIENT_ID + "/")) {
            return true;
        }

        // ② 成员列表 / 全平台用户池：GET 读、POST 新建身份（共享面板 app 作用域仍渲染「新建用户」）。
        //    带 client= 参数时必须等于本应用，否则拒绝。
        if (path.equals("/admin/users")) {
            return ("GET".equals(m) || "POST".equals(m)) && clientScopeOk(query);
        }

        // ③ 单用户编辑：仅 PUT（DELETE / DELETE batch / password 均落空 → 拒绝）。
        if (USER_ONE.matcher(path).matches()) {
            return "PUT".equals(m);
        }

        // ④ 既有「本系统角色」端点：GET 读 / PUT 写，且 client 必须等于本应用。
        if (USER_CLIENT_ROLES.matcher(path).matches()) {
            return ("GET".equals(m) || "PUT".equals(m)) && clientScopeOk(query);
        }

        // ⑤ 既有「菜单减法」端点：GET 读 / PUT 写，且 client 必须等于本应用。
        if (USER_MENU_OVERRIDES.matcher(path).matches()) {
            return ("GET".equals(m) || "PUT".equals(m)) && clientScopeOk(query);
        }

        // ⑥ 角色定义只读：GET /admin/roles（中心各 client 的角色清单，前端据此过滤本应用角色）。
        if (path.equals("/admin/roles") && "GET".equals(m)) {
            return true;
        }

        // 其余（DELETE /admin/users/{id}、PUT .../password、/admin/mappings/**、
        //      /admin/authorization-matrix、/admin/authz/**、/admin/roles 写、其它 client）一律拒绝。
        return false;
    }

    /**
     * 校验 {@code client} 作用域参数：缺省（未传）视为放行；传了则必须等于本应用 {@link #CLIENT_ID}。
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
