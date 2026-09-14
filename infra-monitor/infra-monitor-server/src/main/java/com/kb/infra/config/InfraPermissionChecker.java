package com.kb.infra.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.infra.util.JwtUtil;
import com.marschat.auth.authz.PermissionChecker;
import com.marschat.auth.authz.RequirePermission;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * infra-monitor 侧接口级鉴权实现（2026-09-15 · P1）。
 *
 * <h3>为什么不能直接复用 auth-core 的默认 {@link PermissionChecker}</h3>
 * 默认实现把请求头的 {@code Authorization} <b>原样</b>转发给 auth-center
 * {@code GET /auth/permissions?client=...}。但 infra-monitor 有两种 token：
 * <ol>
 *   <li><b>本应用自签 HS256</b>（{@code /auth/login} 账密、邮箱验证码登录签发）—— 中心不认，
 *       默认 checker 拉取必失败；</li>
 *   <li><b>auth-center OIDC RS256 access token</b>（统一登录用户持有）—— 中心可认。</li>
 * </ol>
 * 若用默认 checker + {@code fail-open=false}，第 1 类用户（含配置式应急管理员）会被全量 403，
 * 应急通道锁死；若 {@code fail-open=true} 则第 1 类用户被静默放行，闸门形同虚设。故本类显式分辨两者。
 *
 * <h3>判定顺序</h3>
 * <ol>
 *   <li>无 token / 无法解析 → <b>放行</b>（401 由 Spring Security 的 {@code JwtAuthFilter}
 *       链路统一出，保持「401=未认证、403=无权限」语义）；</li>
 *   <li><b>本应用自签 HS256</b>：
 *     <ul>
 *       <li>用户名 == {@code infra.admin.username}（配置式应急管理员）→ 放行（应急通道，可关，
 *           开关 {@code infra.authz.local-admin-bypass}）；</li>
 *       <li>其他本地会话（如邮箱码登录签发的 token，请求里没有该用户的中心身份）→ <b>拒绝</b>
 *           （无法以用户本人身份查中心权限，fail-closed，绝不猜放行）；</li>
 *     </ul>
 *   </li>
 *   <li><b>中心 OIDC token</b> → 以该用户本人身份查中心权限：
 *     <ul>
 *       <li>拉取失败 → 返回 {@code failOpen}（本应用显式配 false → 拒绝）；</li>
 *       <li>{@code configured=false} → 放行（R10：应用未配置权限点 = 行为不变）；</li>
 *       <li>平台角色含 admin/superadmin → 放行（与 auth-core 一致）；</li>
 *       <li>命中所需权限点 → 放行，否则 403（由 auth-core 拦截器统一出体）。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>缓存：按 <b>token</b> 分桶（不用 auth-core 默认的单槽缓存，避免多用户互相污染），TTL 60s；
 * 失败不写缓存，下次重试，不掩盖故障。
 */
@Slf4j
public class InfraPermissionChecker extends PermissionChecker {

    private final String issuer;
    private final String clientId;
    private final long cacheTtlMs;
    private final boolean failOpen;
    private final JwtUtil jwtUtil;
    private final String adminUsername;
    private final boolean localAdminBypass;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** token → 缓存的中心权限 data 节点（成功才写）。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 缓存槽上限，超过即整体清空（防止长跑进程无界增长）。 */
    private static final int CACHE_MAX = 1000;

    private record CacheEntry(long fetchedAt, JsonNode data) {}

    public InfraPermissionChecker(String issuer, String clientId, long cacheTtlMs, boolean failOpen,
                                  JwtUtil jwtUtil, String adminUsername, boolean localAdminBypass) {
        // 父类构造仅用于满足类型契约；本类完全覆盖 hasPermission，不走父类的单槽缓存 fetch。
        super(issuer, clientId, cacheTtlMs, failOpen);
        this.issuer = issuer == null ? "" : issuer.replaceAll("/+$", "");
        this.clientId = clientId;
        this.cacheTtlMs = cacheTtlMs;
        this.failOpen = failOpen;
        this.jwtUtil = jwtUtil;
        this.adminUsername = adminUsername;
        this.localAdminBypass = localAdminBypass;
    }

    @Override
    public boolean hasPermission(String bearerToken, String[] required, RequirePermission.Mode mode) {
        if (required == null || required.length == 0) {
            return true;
        }
        // 1) 无 token：放行，由 Spring Security 出 401
        if (bearerToken == null || bearerToken.isBlank()) {
            return true;
        }
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;

        // 2) 本应用自签 HS256 会话（账密 / 邮箱码登录）
        String localUser = jwtUtil.parseLocalUsername(token);
        if (localUser != null) {
            if (localAdminBypass && adminUsername != null && adminUsername.equals(localUser)) {
                log.warn("鉴权放行：本应用应急管理员 {}（本地会话，未经中心权限点校验）", localUser);
                return true;
            }
            // 请求里只有本应用自签 token，拿不到该用户的中心身份去查权限 → 不猜，直接拒绝
            log.warn("鉴权拒绝：本地会话用户 {} 无中心身份可校验（fail-closed，请改用统一登录）", localUser);
            return false;
        }

        // 3) 非本应用签发 → 必须是中心 OIDC token，否则交回 Security 出 401
        if (jwtUtil.parseOidcUsername(token) == null) {
            return true;
        }

        JsonNode data = fetchCached(token);
        if (data == null) {
            log.warn("鉴权{}：拉取中心权限失败（fail-open={}）", failOpen ? "放行" : "拒绝", failOpen);
            return failOpen;
        }
        if (!data.path("configured").asBoolean(false)) {
            return true; // R10：应用未配置任何权限点 = 行为不变
        }
        for (JsonNode r : data.path("platformRoles")) {
            String role = r.asText("");
            if ("admin".equals(role) || "superadmin".equals(role)) {
                return true;
            }
        }
        Set<String> owned = new HashSet<>();
        data.path("permissions").forEach(p -> owned.add(p.asText()));
        long hit = Arrays.stream(required).map(this::qualify).filter(owned::contains).count();
        boolean ok = mode == RequirePermission.Mode.ALL ? hit == required.length : hit > 0;
        if (!ok) {
            log.warn("鉴权拒绝：required={} owned={}", String.join(",", required), owned.size());
        }
        return ok;
    }

    /**
     * 权限点补全 → 中心下发的全码 {@code marschat-inframon:api:<key>}。
     *
     * <p>三种写法都兼容：全码原样、{@code api:items:create}、{@code items:create}。
     */
    private String qualify(String code) {
        if (code.startsWith(clientId + ":")) {
            return code;
        }
        if (code.startsWith("api:")) {
            return clientId + ":" + code;
        }
        return clientId + ":api:" + code;
    }

    /** 按 token 缓存中心权限查询；失败返回 null 且不写缓存（下次重试，不掩盖故障）。 */
    private JsonNode fetchCached(String token) {
        long now = System.currentTimeMillis();
        CacheEntry c = cache.get(token);
        if (c != null && now - c.fetchedAt() < cacheTtlMs) {
            return c.data();
        }
        JsonNode data = fetch(token);
        if (data != null && !data.isMissingNode() && !data.isNull()) {
            if (cache.size() > CACHE_MAX) {
                cache.clear();
            }
            cache.put(token, new CacheEntry(now, data));
        }
        return data;
    }

    /** 以用户本人的 OIDC token 调中心 {@code GET /auth/permissions?client=...}。 */
    private JsonNode fetch(String token) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(issuer + "/auth/permissions?client="
                            + java.net.URLEncoder.encode(clientId, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(resp.body());
            if (body.path("code").asInt() != 200) {
                log.warn("拉取权限失败: HTTP {} {}", resp.statusCode(), body.path("message").asText(""));
                return null;
            }
            return body.path("data");
        } catch (Exception e) {
            log.warn("拉取权限异常: {}", e.getMessage());
            return null;
        }
    }
}
