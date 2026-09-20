package com.kb.infra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marschat.common.result.Result;
import com.kb.infra.service.CenterSessionStore;
import com.kb.infra.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;

/**
 * infra-monitor 登录端点。
 *
 * <p>统一登录三方式（Phase 7）在本应用的落地：
 * <ul>
 *   <li><b>账密</b>：{@code POST /auth/login} —— 由本服务端代理 auth-center 校验后签发自有 token
 *       （原「配置式单管理员」本地比对已于 2026-09-15 移除，见方法注释）；</li>
 *   <li><b>邮箱验证码</b>：{@code POST /auth/mail-login/send-code} + {@code POST /auth/mail-login}
 *       —— 由本服务端代理 auth-center 换票，再签发**本应用自有会话 token**；</li>
 *   <li><b>忘记密码</b>：前端直连 {@code /kb/api/auth/forgot-password}（经 kb-gateway → auth-center）。</li>
 * </ul>
 *
 * <p><b>为什么邮箱码必须走本服务端代理：</b>infra-monitor-server 的 {@code jwt.secret}
 * 与 auth-center **不同源**（2026-09-15 起两者更是各自独立的随机密钥：infra 用 {@code JWT_SECRET}，
 * portal 用 {@code PORTAL_JWT_SECRET}，绝不共享），auth-center 直发的 legacy token 本服务验不过；
 * 且本应用有自有 JwtAuthFilter/白名单体系。故采用与 portal 同构的 BFF 模式：服务端换票 → 签发本应用 token。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    /** 已改由认证中心校验，保留仅为兼容（构造期仍需 infra.admin.password 存在，避免改配置导致启动失败）。 */
    private final String adminPasswordHash;
    /** 账密/邮箱码登录换来的中心 accessToken 暂存（供 /api/admin/** 代理以用户本人身份转发）。 */
    private final CenterSessionStore centerSessions;

    @Value("${auth-center.base:http://127.0.0.1:8085}")
    private String authCenterBase;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param adminPassword 应急管理员口令，来自 {@code infra.admin.password}
     *                      （环境变量 {@code INFRA_ADMIN_PASS}）。
     *                      <b>🔴 2026-09-15 安全修复：已删除代码内 {@code admin123} 默认口令</b>，
     *                      缺失即启动失败（明文弱口令不得入库/入码）。
     */
    public AuthController(JwtUtil jwtUtil,
                          PasswordEncoder passwordEncoder,
                          CenterSessionStore centerSessions,
                          @Value("${infra.admin.username}") String adminUsername,
                          @Value("${infra.admin.password}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.centerSessions = centerSessions;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = passwordEncoder.encode(adminPassword);
    }

    /**
     * 账密登录：BFF 转发 auth-center {@code /auth/login}，成功后签发本应用自有 token。
     *
     * <p>2026-09-15（Phase 11）：原先用 {@code infra.admin.*} 硬编码单管理员做本地比对，
     * 导致中心 42 个身份除该账号外都无法账密登录、且同一应用存在两套认证源。
     * 现统一交由认证中心校验（中心是身份与密码的唯一真源），本服务只负责换票 + 签发自有会话。
     *
     * <p><b>fail-closed</b>：中心不可达或返回异常一律拒绝，绝不回退本地密码比对。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            JsonNode node = authCenterPost("/auth/login", Map.of(
                    "username", request.getUsername().trim(),
                    "password", request.getPassword()));
            if (node.path("code").asInt() != 200) {
                return Result.fail(401, "用户名或密码错误");
            }
            JsonNode user = node.path("data").path("user");
            if (user.path("status").asInt(1) != 1) {
                return Result.fail(401, "账号已停用");
            }
            String username = user.path("username").asText(request.getUsername().trim());
            // role 原样取中心值（中心 admin 为 superadmin）：前端「用户管理」菜单按 token role 兜底判定
            String role = user.path("role").asText("");
            // 中心 accessToken 暂存服务端：「用户管理」需以中心身份调 /admin/**（本应用自签 token 中心不认）
            rememberCenterSession(username, node.path("data"));
            // uid = 中心用户主键：前端 UsersView 据 claims.uid 禁止删除/禁用自己，缺则自我保护失效
            String token = jwtUtil.generate(username, role, centerUid(user));
            return Result.ok(new LoginResponse(token, username));
        } catch (Exception e) {
            log.warn("infra 账密登录失败（认证中心不可达）: {}", e.getMessage());
            return Result.fail(503, "认证中心不可达，请稍后重试");
        }
    }

    /** 邮箱验证码 · 发码（代理 auth-center 的 MAIL_LOGIN 业务码）。 */
    @PostMapping("/mail-login/send-code")
    public Result<Void> sendMailLoginCode(@RequestBody MailCodeRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return Result.fail(400, "缺少邮箱");
        }
        try {
            JsonNode node = authCenterPost("/auth/mail-login/send-code",
                    Map.of("email", request.getEmail().trim()));
            if (node.path("code").asInt() != 200) {
                return Result.fail(node.path("code").asInt(400),
                        node.path("message").asText("发送验证码失败"));
            }
            return Result.ok();
        } catch (Exception e) {
            log.warn("infra 邮箱验证码发码失败: {}", e.getMessage());
            return Result.fail(502, "认证中心不可达，请稍后重试");
        }
    }

    /**
     * 邮箱验证码 · 登录：服务端向 auth-center 换票，成功后签发**本应用自有 token**。
     *
     * <p>双模策略：应急本地账号（账密）不受影响；普通用户走中心统一账号经此登录。
     */
    @PostMapping("/mail-login")
    public Result<LoginResponse> mailLogin(@RequestBody MailLoginRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            return Result.fail(400, "缺少邮箱或验证码");
        }
        try {
            JsonNode node = authCenterPost("/auth/mail-login",
                    Map.of("email", request.getEmail().trim(), "code", request.getCode().trim()));
            if (node.path("code").asInt() != 200) {
                return Result.fail(node.path("code").asInt(400),
                        node.path("message").asText("邮箱验证码登录失败"));
            }
            JsonNode user = node.path("data").path("user");
            String username = user.path("username").asText(adminUsername);
            // 同一处兜底：邮箱码登录同样须带中心 role，否则「用户管理」菜单同样被隐藏
            rememberCenterSession(username, node.path("data"));
            String token = jwtUtil.generate(username, user.path("role").asText(""), centerUid(user));
            log.info("infra 邮箱验证码登录成功: {}", username);
            return Result.ok(new LoginResponse(token, username));
        } catch (Exception e) {
            log.warn("infra 邮箱验证码登录失败: {}", e.getMessage());
            return Result.fail(502, "认证中心不可达，请稍后重试");
        }
    }

    /** 本应用在统一认证中心的 client_id（权限探针作用域钉死，防改 {@code client=} 越界）。 */
    private static final String CLIENT_ID = "marschat-inframon";

    /**
     * 权限探针同源代理（对齐 portal {@code SsoController#permissions} 先例，2026-09-20）。
     *
     * <p>为什么存在：独立登录径下浏览器只持本应用自签 HS384 token，中心验不过（实测直连
     * {@code /auth/permissions} 必 401）→ 独立登录用户权限体系永远 {@code configured=false}
     * 静默失效，且 Console 恒带一条 401 报错。前端 {@code utils/permissions.ts} 已把探针
     * issuer 指向本端点（{@code /infra/api/auth/permissions}）。
     *
     * <p>凭据解析与 {@link AdminProxyController} 的 {@code resolveCenterToken} 同口径：
     * SSO 会话透传浏览器带来的中心 OIDC token；账密/邮箱码会话取 {@link CenterSessionStore}
     * 登录时暂存的中心 accessToken。<b>两路都拿不到 → 401，绝不回退服务账号（防提权）</b>。
     *
     * <p>安全边界：本路径不在 SecurityConfig 匿名白名单（{@code /auth/login} 等为精确匹配），
     * 走 {@code anyRequest().authenticated()}；{@code client} 参数无视调用方传值、恒钉本应用。
     */
    @GetMapping("/permissions")
    public ResponseEntity<String> permissions(HttpServletRequest request) {
        String centerToken = resolveCenterToken(request);
        if (centerToken == null) {
            return json(401, "{\"code\":401,\"message\":\"无统一认证中心会话，请重新登录\",\"data\":null}");
        }
        try {
            HttpRequest forward = HttpRequest.newBuilder()
                    .uri(URI.create(authCenterBase + "/auth/permissions?client=" + CLIENT_ID))
                    .header("Authorization", "Bearer " + centerToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(forward,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 状态码与 Result 信封原样透传：组件按 code===200 解析 data，非 200 fail-open
            return ResponseEntity.status(response.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.body());
        } catch (Exception e) {
            log.warn("权限探针代理失败: {}", e.getMessage());
            return json(502, "{\"code\":502,\"message\":\"认证中心不可达，请稍后重试\",\"data\":null}");
        }
    }

    /**
     * 解析本次请求可用的中心 accessToken（与 AdminProxyController 同款逻辑）：
     * Authorization 非本应用自签（即 SSO 会话的中心 OIDC token）→ 直接透传；
     * 否则按登录时暂存的用户名从 {@link CenterSessionStore} 取。均无 → {@code null}。
     */
    private String resolveCenterToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String presented = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        if (presented != null && !presented.isEmpty() && jwtUtil.parseLocalUsername(presented) == null) {
            return presented;
        }
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : centerSessions.getAccessToken(principal.getName());
    }

    private static ResponseEntity<String> json(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /**
     * 记住本次登录换来的**中心** accessToken（{@code data.accessToken}），供
     * {@code AdminProxyController} 以「该用户本人」的中心身份转发 {@code /admin/**}。
     *
     * <p>为什么不由服务端再登一次中心拿 token：那等于用**服务账号**调管理接口，
     * 中心审计记录不到真实操作者，且一旦兜底就是提权（普通用户拿到管理员能力）。
     * 这里存的始终是**用户自己账密换来的** token。
     *
     * <p>为什么不回传浏览器存 localStorage：那等于把中心管理凭据暴露给页面（XSS 可直接窃取，
     * 且不受本应用登出控制）。服务端按用户名暂存，与 portal 的 refreshTokens 同一模式。
     */
    private void rememberCenterSession(String username, JsonNode data) {
        String accessToken = data.path("accessToken").asText("");
        if (accessToken.isEmpty()) {
            log.warn("中心登录响应未含 accessToken，{} 的用户管理功能将不可用", username);
            return;
        }
        centerSessions.put(username, accessToken, data.path("expiresIn").asLong(0L));
    }

    /** 调用 auth-center 的公开 JSON 端点，返回完整 Result 信封（code/message/data）。 */
    private JsonNode authCenterPost(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authCenterBase + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return MAPPER.readTree(response.body());
    }

    /**
     * 取中心用户主键（{@code data.user.id}）用于 token 的 {@code uid} 声明。
     *
     * <p>中心响应缺失/非数字时返回 {@code null}（不写该声明）——宁可让前端自我保护退化，
     * 也不伪造一个错误的 uid 导致误判他人。
     */
    private Long centerUid(JsonNode user) {
        JsonNode id = user.path("id");
        return id.isNumber() ? id.asLong() : null;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class MailCodeRequest {
        private String email;
    }

    @Data
    public static class MailLoginRequest {
        private String email;
        private String code;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String username;

        public LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }
}
