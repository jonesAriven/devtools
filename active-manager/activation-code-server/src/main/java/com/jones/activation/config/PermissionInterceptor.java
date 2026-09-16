package com.jones.activation.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jones.activation.entity.AdminUser;
import com.jones.activation.service.CenterSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口级权限闸门（Spring MVC {@code HandlerInterceptor}）——「哪些账号有哪些**系统权限**」
 * 在 activecode 接口层的落点。
 *
 * <h3>背景（P0-3，2026-09-16 实测）</h3>
 * activecode 此前是「有权限点、零闸门」：{@code menu-registry.yml} 已上报 6 个 api 权限点，
 * 中心 {@code sys_permission} 里也有 14 条 activecode 权限点，但应用侧无人消费 ——
 * 全仓 grep {@code @RequirePermission} / {@code PermissionChecker} 0 命中。
 * 结果：{@link AuthInterceptor} 只判「有没有登录」，写接口**只认证不鉴权**，
 * 任何登录用户都能删除/批量删除/改别名/改配置；更严重的是
 * {@code POST /activecode/api/activation/generate} 被整条排除出鉴权，**匿名即可调用**。
 *
 * <h3>范式来源（与 kb-gateway 的 PermissionAuthzFilter 同构，载体不同）</h3>
 * ⚠️ kb-gateway 的 {@code PermissionAuthzFilter} 是 **WebFlux {@code GlobalFilter}**；
 * activecode 是 **Spring MVC**。二者**范式一致（命中规则 → 查中心权限点 → 无点则 403 /
 * 中心不可达则 fail-closed），载体不同**：本类用 MVC 的 {@link HandlerInterceptor} 实现。
 *
 * <h3>判定语义（与 auth-core / cosmic / infra / kb-gateway 同一套）</h3>
 * <ol>
 *   <li>总开关 {@link AuthzProperties#isEnabled()} 关闭 → 放行（可回滚）；</li>
 *   <li>{@code OPTIONS} → 放行（CORS 预检）；</li>
 *   <li>非写方法（GET/HEAD…）→ 放行 —— 读=可见性，留给菜单/前端守卫；写=动作，必须后端拦；</li>
 *   <li>按 {@link AuthzProperties} 的规则表匹配「方法 + URI」，未命中 → 放行；</li>
 *   <li>命中则取**用户本人**的中心令牌（{@link CenterSessionStore}）；取不到 → <b>401</b>；</li>
 *   <li>以该令牌调中心 {@code GET /auth/permissions?client=marschat-activecode}：
 *     <ul>
 *       <li>{@code configured=false} → 放行（R10：中心未配置权限点 = 行为不变）；</li>
 *       <li>平台角色含 admin/superadmin → 放行（超管全权）；</li>
 *       <li>命中所需权限点 → 放行；否则 <b>403</b>；</li>
 *     </ul>
 *   </li>
 *   <li>中心不可达 / 解析失败 → 按 {@code fail-open}（**默认 false = 拒绝**）。</li>
 * </ol>
 *
 * <p>结果按「token 指纹」缓存 {@code cache-ttl-ms}（默认 60s），收权延迟上限 = 缓存窗口。
 * 失败不写缓存，下次重试，不掩盖故障。
 *
 * <h3>为什么令牌取自 CenterSessionStore 而不是请求头</h3>
 * activecode 的会话模型是服务端 {@code HttpSession}，浏览器侧**没有**中心凭据
 * （中心 accessToken 由登录时按用户名暂存在 {@link CenterSessionStore}）。
 * 故本类复用 {@code AdminProxyController#resolveCenterToken} 同口径：
 * 优先 {@code session.ssoUser}（SSO / 邮箱码径登记的中心用户名），回退
 * {@code session.loginUser.getUsername()}（账密径的影子账号用户名，二者同名）。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 视为「写动作」的方法：只有这些方法才需要权限点闸门。 */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** 缓存槽上限，超过即清理过期项（防止长跑进程无界增长）。 */
    private static final int CACHE_MAX = 2048;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final AuthzProperties authz;
    private final CenterSessionStore centerSessions;

    /** token 指纹 → {过期时间, 判定快照}（成功才写）。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PermissionInterceptor(AuthzProperties authz, CenterSessionStore centerSessions) {
        this.authz = authz;
        this.centerSessions = centerSessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (authz == null || !authz.isEnabled()) {
            return true;
        }
        // OPTIONS 预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 只拦写方法：读=可见性，写=动作
        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }

        String uri = request.getRequestURI();
        String perm = matchPerm(authz, request.getMethod(), uri);
        if (perm == null) {
            return true; // 未命中的路径不受管（含 verify、auth、admin 代理等）
        }

        String token = resolveCenterToken(request);
        if (!StringUtils.hasText(token)) {
            log.warn("接口权限闸门：命中规则但无中心会话 uri={} perm={}", uri, perm);
            deny(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或缺少统一认证中心会话");
            return false;
        }

        String required = fullCode(authz.getClientId(), perm);
        Verdict verdict = resolveVerdict(authz, token);
        if (verdict == null) {
            // 无法判定（中心不可达 / 解析失败）
            if (authz.isFailOpen()) {
                log.warn("权限判定不可用，fail-open 放行: uri={}, perm={}", uri, required);
                return true;
            }
            log.warn("权限判定不可用，fail-closed 拒绝: uri={}, perm={}", uri, required);
            deny(response, HttpServletResponse.SC_FORBIDDEN, "权限校验服务不可用，已拒绝该操作");
            return false;
        }
        if (!verdict.allowed(required)) {
            log.warn("接口权限不足: uri={}, perm={}", uri, required);
            deny(response, HttpServletResponse.SC_FORBIDDEN, "无权限");
            return false;
        }
        return true;
    }

    /**
     * 命中规则则返回权限点短码，否则 {@code null}。
     *
     * <p>同时校验 HTTP 方法：规则 {@code method} 留空表示命中任意写方法；否则要求大小写不敏感相等。
     * 规则按 yml 顺序匹配，命中第一条即返回。
     */
    private String matchPerm(AuthzProperties authz, String httpMethod, String uri) {
        if (authz.getRules() == null || authz.getRules().isEmpty()) {
            return null;
        }
        for (AuthzProperties.Rule rule : authz.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getPattern()) || !StringUtils.hasText(rule.getPerm())) {
                continue;
            }
            if (StringUtils.hasText(rule.getMethod())
                    && !rule.getMethod().equalsIgnoreCase(httpMethod)) {
                continue;
            }
            if (PATH_MATCHER.match(rule.getPattern(), uri)) {
                return rule.getPerm();
            }
        }
        return null;
    }

    /**
     * 取本次请求对应的**用户本人**中心 accessToken；无则返回 {@code null}。
     *
     * <p>与 {@code AdminProxyController#resolveCenterToken} 同口径：优先 {@code ssoUser}
     * （SSO / 邮箱码径登记的中心用户名），回退 {@code loginUser}（账密径影子账号用户名）。
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

    /** 拉取并判定（带缓存）。返回 {@code null} 表示「无法判定」，交由 fail-open/fail-closed 决定。 */
    private Verdict resolveVerdict(AuthzProperties authz, String token) {
        String key = fingerprint(token);
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expireAt() > now) {
            return hit.verdict();
        }
        Verdict verdict = fetchVerdict(authz, token);
        if (verdict != null) {
            if (cache.size() > CACHE_MAX) {
                cache.entrySet().removeIf(e -> e.getValue().expireAt() <= System.currentTimeMillis());
            }
            cache.put(key, new CacheEntry(now + authz.getCacheTtlMs(), verdict));
        }
        return verdict;
    }

    /** 以「用户本人 token」向 auth-center 查该用户在 marschat-activecode 的权限点。 */
    private Verdict fetchVerdict(AuthzProperties authz, String token) {
        try {
            String base = authz.getIssuer() == null ? "" : authz.getIssuer().replaceAll("/+$", "");
            String url = base + "/auth/permissions?client="
                    + URLEncoder.encode(authz.getClientId(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("拉取中心权限失败 http={} client={}", resp.statusCode(), authz.getClientId());
                return null;
            }
            JsonNode body = MAPPER.readTree(resp.body());
            JsonNode data = body.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            Verdict v = new Verdict();
            v.configured = data.path("configured").asBoolean(false);
            for (JsonNode r : data.path("platformRoles")) {
                v.platformRoles.add(r.asText());
            }
            for (JsonNode p : data.path("permissions")) {
                v.permissions.add(p.asText());
            }
            return v;
        } catch (Exception e) {
            log.warn("拉取中心权限异常 client={}: {}", authz.getClientId(), e.getMessage());
            return null;
        }
    }

    /** 权限点补全 → 中心下发的全码 {@code marschat-activecode:api:<key>}。 */
    private String fullCode(String clientId, String perm) {
        return perm.startsWith(clientId + ":") ? perm : clientId + ":api:" + perm;
    }

    /** 以 SHA-256 前 12 字节作为 token 指纹（不落原文，仅作缓存 key）。 */
    private String fingerprint(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return token.substring(0, Math.min(24, token.length()));
        }
    }

    /** 统一拒绝响应体：{@code {"success":false,"message":"..."}}。 */
    private void deny(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Map.of("success", false, "message", message)));
    }

    private record CacheEntry(long expireAt, Verdict verdict) {
    }

    /** 一次判定的快照。 */
    private static final class Verdict {
        boolean configured;
        final Set<String> permissions = new LinkedHashSet<>();
        final Set<String> platformRoles = new LinkedHashSet<>();

        boolean allowed(String fullCode) {
            if (!configured) {
                return true; // R10：中心未配置权限点 = 行为不变
            }
            if (platformRoles.contains("admin") || platformRoles.contains("superadmin")) {
                return true;
            }
            return permissions.contains(fullCode);
        }
    }
}
