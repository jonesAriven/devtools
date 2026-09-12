package com.kb.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * auth-center OIDC 客户端 + 管理代理。
 * - 授权码换 token（client_secret_basic）
 * - refresh_token 换新 access token（SAS 默认不轮换失效，重放安全）
 * - /admin/users 代理：携带 RS256 access token，401 自动刷新重试一次
 * - refresh token 按.portal 用户 id 存内存（7 天有效，portal 重启需重新 SSO）
 */
@Slf4j
@Service
public class AuthCenterService {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${auth-center.issuer:http://192.168.31.105:8085}")
    private String issuer;
    @Value("${auth-center.client-id:marschat-portal}")
    private String clientId;
    @Value("${auth-center.client-secret:portal-secret-2026}")
    private String clientSecret;
    @Value("${auth-center.admin-api:${auth-center.issuer:http://192.168.31.105:8085}}")
    private String adminApi;
    @Value("${auth-center.admin-username:admin}")
    private String adminUsername;
    @Value("${auth-center.admin-password:admin123}")
    private String adminPassword;

    /** portal 用户 id -> refresh_token */
    private final Map<Long, String> refreshTokens = new ConcurrentHashMap<>();
    /** state -> redirect origin（5 分钟有效） */
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();
    /** 服务级 legacy token 缓存（密码登录的管理员没有 SSO 会话，用服务身份兜底调管理 API） */
    private volatile String serviceToken;
    private volatile long serviceTokenExpiresAt;

    private record StateEntry(String redirectOrigin, long expiresAt) {}

    public String buildAuthorizeUrl(String redirectOrigin) {
        String state = java.util.UUID.randomUUID().toString().replace("-", "");
        states.put(state, new StateEntry(redirectOrigin, System.currentTimeMillis() + 300_000));
        String redirectUri = redirectOrigin + "/portal/auth/callback";
        return issuer + "/oauth2/authorize?response_type=code&client_id=" + clientId
                + "&scope=openid%20profile&state=" + state
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
    }

    /** 校验并消费 state，返回发起时的 origin */
    public String consumeState(String state) {
        StateEntry entry = states.remove(state);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry.redirectOrigin();
    }

    /** 授权码换 token，返回解析后的 token JSON */
    public JsonNode exchangeCode(String code, String redirectOrigin) throws Exception {
        String redirectUri = redirectOrigin + "/portal/auth/callback";
        String form = "grant_type=authorization_code&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return tokenRequest(form);
    }

    private JsonNode tokenRequest(String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer + "/oauth2/token"))
                .header("Authorization", basicAuth(clientId, clientSecret))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        if (response.statusCode() != 200 || !node.has("access_token")) {
            log.warn("auth-center token 请求失败: {} {}", response.statusCode(), response.body());
            throw new IllegalStateException("统一认证换 token 失败");
        }
        return node;
    }

    /** 用.refresh_token 换新 access token；换发失败即清掉陈旧 token */
    public String refreshAccessToken(Long portalUserId) throws Exception {
        String refresh = refreshTokens.get(portalUserId);
        if (refresh == null) {
            throw new IllegalStateException("SSO 会话不存在，请重新统一登录");
        }
        try {
            JsonNode node = tokenRequest("grant_type=refresh_token&refresh_token="
                    + URLEncoder.encode(refresh, StandardCharsets.UTF_8));
            if (node.has("refresh_token")) {
                refreshTokens.put(portalUserId, node.get("refresh_token").asText());
            }
            return node.get("access_token").asText();
        } catch (IllegalStateException e) {
            // refresh token 失效（过期/被撤销/认证中心侧会话丢失），清掉陈旧值走服务身份兜底
            refreshTokens.remove(portalUserId);
            throw new IllegalStateException("SSO 会话已失效，请重新统一登录");
        }
    }

    public void storeRefreshToken(Long portalUserId, JsonNode tokenJson) {
        if (tokenJson.has("refresh_token")) {
            refreshTokens.put(portalUserId, tokenJson.get("refresh_token").asText());
        }
    }

    /** 解析 RS256 access token 的 payload（不验签，仅取映射用 claims；安全依赖 exchange 直连 auth-center） */
    public JsonNode parseAccessTokenClaims(String accessToken) throws Exception {
        String[] parts = accessToken.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1] + "=".repeat((4 - parts[1].length() % 4) % 4));
        return objectMapper.readTree(payload);
    }

    /**
     * 管理代理调用：
     * 1) 优先 SSO 身份（RS256，auth-center 审计记录真实操作者）；refresh 失效自动清陈旧 token；
     * 2) 兜底服务身份（legacy HS512，密码登录的管理员没有 SSO 会话也可用用户管理）；
     * 3) 两者都 401 才对外报 401。
     */
    public ProxyResult callAdmin(Long portalUserId, String method, String pathWithQuery, String jsonBody) {
        // ---- SSO 身份 ----
        if (portalUserId != null && refreshTokens.containsKey(portalUserId)) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    String accessToken = refreshAccessToken(portalUserId);
                    ProxyResult r = doAdminCall(accessToken, method, pathWithQuery, jsonBody);
                    if (r.status() != 401) {
                        return r;
                    }
                    if (attempt == 0) {
                        continue; // access token 可能刚轮换，强制再刷一次
                    }
                    refreshTokens.remove(portalUserId); // SSO 身份反复 401，会话已无效
                } catch (IllegalStateException e) {
                    break; // 陈旧 refresh token 已被 refreshAccessToken 清掉，转服务身份
                } catch (Exception e) {
                    log.warn("auth-center 管理代理 SSO 调用失败: {}", e.getMessage());
                    break;
                }
            }
        }
        // ---- 服务身份兜底 ----
        try {
            ProxyResult r = doAdminCall(getServiceToken(), method, pathWithQuery, jsonBody);
            if (r.status() == 401) {
                serviceToken = null; // 强制重登
                r = doAdminCall(getServiceToken(), method, pathWithQuery, jsonBody);
            }
            return r;
        } catch (Exception e) {
            log.warn("auth-center 管理代理服务身份调用失败: {}", e.getMessage());
            return new ProxyResult(502, "{\"code\":502,\"message\":\"统一认证中心不可达\",\"data\":null}");
        }
    }

    /**
     * 以**用户本人身份**调用 auth-center，**绝不做服务身份兜底**（Phase 2 · RBAC 权限下发）。
     *
     * 🔴 为什么不能复用 {@link #callAdmin}：callAdmin 在用户没有 SSO 会话时会**回退到服务账号**。
     * 对「用户管理」这是合理的（密码登录的管理员也要能用）；但对「权限查询」是**提权漏洞**——
     * 服务账号是 admin，兜底会把管理员的权限集合下发给一个普通用户。
     *
     * 因此这里严格只用调用者自己的 auth-center 身份：
     * - 无 SSO 会话（密码登录 / refresh 已失效）→ 返回 401，前端 `usePermissions` 按
     *   `configured=false` 降级为全放行（R10 默认策略），**不会**误放行也不提权。
     * - auth-center 不可达 → 返回 502，同样降级。
     *
     * @param portalUserId portal 用户 id（JwtInterceptor 注入的 request attribute）
     */
    public ProxyResult callAsUser(Long portalUserId, String method, String pathWithQuery, String jsonBody) {
        if (portalUserId == null || !refreshTokens.containsKey(portalUserId)) {
            return new ProxyResult(401,
                    "{\"code\":401,\"message\":\"无统一认证会话\",\"data\":null}");
        }
        try {
            String accessToken = refreshAccessToken(portalUserId);
            return doAdminCall(accessToken, method, pathWithQuery, jsonBody);
        } catch (Exception e) {
            log.warn("auth-center 用户身份代理调用失败: {}", e.getMessage());
            return new ProxyResult(502,
                    "{\"code\":502,\"message\":\"统一认证中心不可达\",\"data\":null}");
        }
    }

    private ProxyResult doAdminCall(String accessToken, String method, String pathWithQuery, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(adminApi + pathWithQuery))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));
        switch (method.toUpperCase()) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ProxyResult(response.statusCode(), response.body());
    }

    /** 服务账号登录 auth-center legacy 接口，缓存 token 到期前 60s */
    private synchronized String getServiceToken() throws Exception {
        if (serviceToken != null && System.currentTimeMillis() < serviceTokenExpiresAt) {
            return serviceToken;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(issuer + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(Map.of("username", adminUsername, "password", adminPassword))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        String token = node.path("data").path("accessToken").asText(null);
        if (response.statusCode() != 200 || node.path("code").asInt() != 200 || token == null) {
            throw new IllegalStateException("auth-center 服务账号登录失败: HTTP " + response.statusCode()
                    + " body=" + response.body());
        }
        serviceToken = token;
        long expiresIn = node.path("data").path("expiresIn").asLong(3_600_000L);
        serviceTokenExpiresAt = System.currentTimeMillis() + Math.max(60_000, expiresIn - 60_000);
        return serviceToken;
    }

    public record ProxyResult(int status, String body) {}

    private String basicAuth(String user, String pass) {
        String value = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
