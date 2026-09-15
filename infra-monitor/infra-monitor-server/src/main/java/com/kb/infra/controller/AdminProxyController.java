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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;

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
 *   <li>角色判定不在本代理做：中心返回什么状态就原样透传（非管理员 → 中心 403，token 过期 → 中心 401）。</li>
 * </ul>
 *
 * <p>为什么用「前缀透传」而非逐端点声明：中心每新增一个 {@code /admin/**} 端点，消费方零改动即可用；
 * 且请求方法、查询串（keyword/page/size/client）、请求体原样转发，不会像逐端点那样丢参数
 * （portal 曾因此丢掉分页与应用作用域过滤参数）。
 */
@Slf4j
@RestController
public class AdminProxyController {

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

    private ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
