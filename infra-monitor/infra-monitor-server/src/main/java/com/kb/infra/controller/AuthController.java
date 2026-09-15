package com.kb.infra.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marschat.common.result.Result;
import com.kb.infra.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
                          @Value("${infra.admin.username}") String adminUsername,
                          @Value("${infra.admin.password}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
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
            String token = jwtUtil.generate(username);
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
            String username = node.path("data").path("user").path("username").asText(adminUsername);
            String token = jwtUtil.generate(username);
            log.info("infra 邮箱验证码登录成功: {}", username);
            return Result.ok(new LoginResponse(token, username));
        } catch (Exception e) {
            log.warn("infra 邮箱验证码登录失败: {}", e.getMessage());
            return Result.fail(502, "认证中心不可达，请稍后重试");
        }
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
