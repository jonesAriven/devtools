package com.jones.activation.controller;

import com.jones.activation.entity.AdminUser;
import com.jones.activation.service.CenterSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Duration;

/**
 * 统一认证中心 {@code /admin/**} 的 BFF 前缀透传代理（本系统用户管理页的数据源）。
 *
 * <h3>为什么需要它</h3>
 * activecode 的会话是服务端 {@code HttpSession}，浏览器侧**没有**中心凭据，
 * 直连中心 {@code /admin/**} 必然 401。本代理把请求改为同源 → 本服务以**用户本人持有的
 * 中心 token** 转发，与其它 5 个应用（portal/infra/kb-ops/kb-web/cosmic）同口径。
 *
 * <h3>凭据从哪来</h3>
 * 登录时中心返回的 {@code data.accessToken} 由 {@link CenterSessionStore} 按用户名暂存
 * （账密径在 {@code AuthController#login}、SSO/邮箱码径在 {@code establishSession}），
 * 此处按会话里的中心用户名取用。
 *
 * <h3>安全边界（务必保持）</h3>
 * <ul>
 *   <li><b>绝不使用服务账号兜底</b>：只转发调用方自己的 token。历史上正是「无会话时回退
 *       服务账号」造成提权（服务账号是 admin，会把管理员能力下发给普通用户）。</li>
 *   <li>拿不到中心凭据 → 直接 {@code 401}，不转发、不伪造，让前端走正常重授权。</li>
 *   <li>只代理 {@code /activecode/api/admin/**}；中心的 {@code /internal/**}（应用上报内网通道，
 *       带 secret）与 {@code /auth/**} 一律不在透传范围内，不会被本代理暴露。</li>
 *   <li><b>角色判定不在本代理做</b>：中心返回什么状态就原样透传（非管理员 → 中心 403，
 *       token 过期 → 中心 401）。这与 infra-monitor 的 AdminProxyController 同一取舍：
 *       应用侧不复制一份平台角色判定，避免两处真源不一致。</li>
 * </ul>
 *
 * <p>为什么用「前缀透传」而非逐端点声明：中心每新增一个 {@code /admin/**} 端点，消费方零改动
 * 即可用；且请求方法、查询串（keyword/page/size/client）、请求体原样转发，不会像逐端点那样丢参数。
 */
@RestController
public class AdminProxyController {

    private static final Logger log = LoggerFactory.getLogger(AdminProxyController.class);

    private final CenterSessionStore centerSessions;

    /** 中心内网基址（与 AuthController 同口径：独立主机，必须走宿主 LAN 地址） */
    @Value("${marschat.auth-center.base:http://192.168.31.105:8085}")
    private String authCenterBase;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AdminProxyController(CenterSessionStore centerSessions) {
        this.centerSessions = centerSessions;
    }

    /**
     * 代理中心 {@code /admin/**}。
     *
     * <p>请求 {@code /activecode/api/admin/users?client=marschat-activecode}
     * → 中心 {@code /admin/users?client=marschat-activecode}。
     */
    @RequestMapping("/activecode/api/admin/**")
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
                    .uri(URI.create(authCenterBase.replaceAll("/+$", "") + path))
                    .header("Authorization", "Bearer " + centerToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10));
            switch (request.getMethod().toUpperCase()) {
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
            log.warn("认证中心管理代理失败（{} {}）: {}", request.getMethod(), path, e.getMessage());
            return json(502, "{\"code\":502,\"message\":\"认证中心不可达，请稍后重试\",\"data\":null}");
        }
    }

    /**
     * 取本次请求对应的**中心** accessToken；无则返回 {@code null}。
     *
     * <p>用户名优先取 {@code ssoUser}（SSO / 邮箱码径登记的中心用户名），回退 {@code loginUser}
     * （账密径的影子账号用户名 —— 二者同名，见 AuthController 的收敛逻辑）。
     */
    private String resolveCenterToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object ssoUser = session.getAttribute("ssoUser");
        String username = (ssoUser instanceof String s && !s.isBlank()) ? s : null;
        if (username == null) {
            Object loginUser = session.getAttribute("loginUser");
            if (loginUser instanceof AdminUser u) {
                username = u.getUsername();
            }
        }
        return centerSessions.getAccessToken(username);
    }

    /**
     * 从请求 URI 截出中心路径并拼回查询串：
     * {@code /activecode/api/admin/users?x=1} → {@code /admin/users?x=1}。
     *
     * <p>用 {@code indexOf("/api/admin")} 而非按 context-path 长度裁剪：后者在反代改写时
     * 会切错位置（infra-monitor 踩过）。
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
