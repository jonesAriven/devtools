package com.kb.portal.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.kb.portal.service.AuthCenterService;
import com.kb.portal.util.JwtUtil;
import com.marschat.auth.authz.PermissionChecker;
import com.marschat.auth.authz.RequirePermission;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * portal 侧接口级鉴权实现（Phase 9 / G3 · 参考实现）。
 *
 * <h3>为什么需要它（而不是直接用 auth-core 的 PermissionChecker）</h3>
 * auth-core 的 {@link PermissionChecker} 把**请求头里的 Authorization 原样**转发给
 * auth-center {@code GET /auth/permissions?client=...}。但 portal 是 OIDC **机密客户端（BFF）**：
 * 浏览器里只有 portal-server 自签的 **HS256 {@code portal_token}**，auth-center 不认 →
 * 默认 checker 的 fetch 必失败 → 落到 {@code failOpen}（默认 true）→ **静默放行**，
 * 「看起来接上了，实则毫无作用」。故本类以**该用户本人的 RS256 access token**去查中心权限。
 *
 * <h3>身份来源与「绝不提权」</h3>
 * 经 {@link AuthCenterService#fetchPermissionsAsUser}（内部即 {@code callAsUser}）：只用
 * 调用者本人的中心身份，**绝不回退服务账号**（服务账号是 admin，回退=凭服务账号放行）。
 * 无 SSO 会话 = 拿不到用户身份 → 按 fail-closed 处理（见下）。
 *
 * <h3>判定顺序（与 auth-core 语义对齐）</h3>
 * <ol>
 *   <li><b>无/非法 token</b> → 放行（把 401 交回既有 {@code JwtInterceptor}，保持登录语义不破）；</li>
 *   <li>portal_token 的 {@code role=superadmin}（中心 {@code user.role} 登录时快照）→ <b>恒放行</b>；</li>
 *   <li>有 SSO 会话 → 查中心权限：
 *     <ul>
 *       <li>拉取失败/中心不可达 → <b>拒绝（fail-closed）</b>；</li>
 *       <li>{@code configured=false} → 放行（R10：应用未配置权限点 = 行为不变）；</li>
 *       <li>中心 {@code platformRoles} 含 admin/superadmin → 放行（与 auth-core 一致）；</li>
 *       <li>命中所需权限点 → 放行，否则 403（由 auth-core 拦截器统一出体）；</li>
 *     </ul>
 *   </li>
 *   <li><b>无 SSO 会话</b>（密码/邮箱码登录，或 portal 重启内存清空）→ <b>拒绝</b>。
 *       取舍说明：管理面刻意 fail-closed（宁可短暂不可用，也不静默放行）；超管由第 2 步兜住，
 *       故不会锁死超管。若线上确需「无会话时沿用旧 requireAdmin 的 admin/superadmin 判定」，
 *       把本步改为「返回 {@code "admin".equals(role) || "superadmin".equals(role)}}」即可（一行降级）。</li>
 * </ol>
 *
 * <p>缓存：按 {@code userId} 分桶，TTL 默认 60s（{@code marschat.authz.cache-ttl-ms}）；
 * 失败**不缓存**，下次请求重试（fail-closed 语义下不掩盖故障）。
 */
@Slf4j
public class PortalPermissionChecker extends PermissionChecker {

    private final JwtUtil jwtUtil;
    private final AuthCenterService authCenterService;
    private final String clientId;
    private final long cacheTtlMs;

    /** userId → 缓存的中心权限 data 节点（成功才写）。 */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(long fetchedAt, JsonNode data) {}

    public PortalPermissionChecker(AuthCenterService authCenterService, JwtUtil jwtUtil,
                                   String issuer, String clientId, long cacheTtlMs) {
        // 父类构造仅用于满足类型契约；本类**完全覆盖** hasPermission，不使用父类的 HTTP/fetch。
        super(issuer, clientId, cacheTtlMs, false);
        this.authCenterService = authCenterService;
        this.jwtUtil = jwtUtil;
        this.clientId = clientId;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public boolean hasPermission(String bearerToken, String[] required, RequirePermission.Mode mode) {
        if (required == null || required.length == 0) {
            return true;
        }
        // 1) 无 token：放行，由 JwtInterceptor 统一出 401（保持既有登录语义）
        if (bearerToken == null || bearerToken.isBlank()) {
            return true;
        }
        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;

        // 非法 / 过期 portal_token：同样交回 JwtInterceptor → 401
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            return true;
        }

        // 2) portal 超管（中心 user.role=superadmin 的快照）恒放行
        String role = jwtUtil.getRole(token);
        if ("superadmin".equals(role)) {
            return true;
        }

        // 3) 无 SSO 会话：拿不到「用户本人」的中心身份 → fail-closed（见类注释第 4 点取舍）
        if (!authCenterService.hasSsoSession(userId)) {
            log.warn("鉴权拒绝：用户 {} 无统一认证会话（fail-closed）", userId);
            return false;
        }

        // 4) 有会话：以用户本人身份查中心权限（按 userId 缓存 60s）
        JsonNode data = fetchCached(userId);
        if (data == null) {
            // auth-center 不可达 / 解析失败 → 刻意 fail-closed（管理面；见 application.yml 注释）
            log.warn("鉴权拒绝：拉取用户 {} 中心权限失败（fail-closed）", userId);
            return false;
        }
        if (!data.path("configured").asBoolean(false)) {
            return true; // R10：应用未配置任何权限点 = 行为不变
        }
        for (JsonNode r : data.path("platformRoles")) {
            String pr = r.asText("");
            if ("superadmin".equals(pr)) {
                // 平台超管恒放行（G3 判定规则：持有权限点 或 平台 superadmin）。
                // 注意：此处**只放行 superadmin**（比 auth-core 默认更严——默认还会放行 admin）；
                // 平台 admin 必须显式持有 api:admin，闸门才算真实生效。
                return true;
            }
        }
        Set<String> owned = new HashSet<>();
        data.path("permissions").forEach(p -> owned.add(p.asText()));
        long hit = Arrays.stream(required).map(this::qualify).filter(owned::contains).count();
        boolean ok = mode == RequirePermission.Mode.ALL ? hit == required.length : hit > 0;
        if (!ok) {
            log.warn("鉴权拒绝：user={} required={} owned={}", userId, String.join(",", required), owned.size());
        }
        return ok;
    }

    /** 权限码补全：{@code api:admin} → {@code <clientId>:api:admin}（全码原样）。 */
    private String qualify(String code) {
        return code.startsWith(clientId + ":") ? code : clientId + ":" + code;
    }

    /** 按 userId 缓存中心权限查询；失败返回 null 且不写缓存（下次重试，不掩盖故障）。 */
    private JsonNode fetchCached(Long userId) {
        long now = System.currentTimeMillis();
        CacheEntry c = cache.get(userId);
        if (c != null && now - c.fetchedAt() < cacheTtlMs) {
            return c.data();
        }
        JsonNode data = authCenterService.fetchPermissionsAsUser(userId, clientId);
        if (data != null && !data.isMissingNode() && !data.isNull()) {
            cache.put(userId, new CacheEntry(now, data));
        }
        return data;
    }
}
