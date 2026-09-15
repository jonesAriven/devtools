package com.kb.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.gateway.config.KbGatewayProperties;
import com.marschat.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口级权限闸门（Reactive GlobalFilter）——「哪些账号有哪些**系统权限**」在接口层的落点。
 *
 * <h3>背景（实测，ADR §36.10-L3）</h3>
 * kb-web 的写接口此前**只有认证没有鉴权**：{@link JwtAuthFilter} 只验签，任何持有有效
 * access token 的用户都能调 `POST /kb/api/doc/**`、`DELETE /kb/api/file/**` 等写接口。
 * 中心侧 kbweb 的 api 权限点也是 **0 条** —— 「权限统一管理」在 kb-web 这条链上没落地。
 *
 * <h3>为什么在网关收口（而不是改三个业务服务）</h3>
 * kb-file / kb-knowledge / kb-intelligence 只依赖 common-core（**无 auth-core**、
 * 无 Servlet Security 链、且部分是 WebFlux 之外的普通 MVC 服务），逐个接入
 * `@RequirePermission` 需要给三个服务加依赖 + 配置 + 改造 + 回归；
 * 而网关**已经**持有「已验签的用户身份 + 原始 token」，一处收口即可覆盖全部下游。
 *
 * <h3>判定语义（与 auth-core / cosmic / infra 同一套）</h3>
 * <ol>
 *   <li>只拦**写方法**（POST/PUT/PATCH/DELETE）且**命中 rules** 的路径 —— 读=可见性，留给菜单/前端；</li>
 *   <li>{@code configured=false} → 放行（R10：应用未配置权限点 = 行为不变）；</li>
 *   <li>平台 admin/superadmin → 放行（超管全权）；</li>
 *   <li>命中权限点 → 放行；否则 **403**；</li>
 *   <li>中心不可达 / 解析失败 → 按 {@code fail-open}（**默认 false = 拒绝**）；
 *       只影响命中的写请求，读与未命中路径不受影响。</li>
 * </ol>
 * 结果按「token 指纹」缓存 {@code cache-ttl-ms}（默认 60s），收权延迟上限 = 缓存窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionAuthzFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<HttpMethod> WRITE_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final ObjectMapper objectMapper;
    private final KbGatewayProperties gatewayProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** token 指纹 → {过期时间, 判定数据} */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        // 必须晚于 JwtAuthFilter（+10）：依赖它已完成验签并注入身份
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        KbGatewayProperties.Authz authz = gatewayProperties.getAuthz();
        if (authz == null || !authz.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        // 只拦写方法：读=可见性（菜单/前端守卫负责），写=动作（必须后端拦）
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        String contextPath = gatewayProperties.getContextPath();
        if (contextPath == null) {
            contextPath = "/kb";
        }
        if (!path.startsWith(contextPath)) {
            return chain.filter(exchange);
        }
        // 规则里的 pattern 是**相对 contextPath** 的（如 /api/doc/**），便于配置可读
        String relative = path.substring(contextPath.length());
        String perm = matchPerm(authz, relative);
        if (perm == null) {
            return chain.filter(exchange); // 未命中的路径不受管
        }

        String traceId = request.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            // JwtAuthFilter 已在前置拦截无 token 请求；此处为防御性兜底
            return deny(exchange, "未登录或缺少访问令牌", traceId, HttpStatus.UNAUTHORIZED);
        }

        final String required = fullCode(authz.getClientId(), perm);
        return resolveVerdict(authz, token)
                .flatMap(verdict -> {
                    if (verdict == null) {
                        // 无法判定（中心不可达/解析失败）
                        if (authz.isFailOpen()) {
                            log.warn("权限判定不可用，fail-open 放行: path={}, perm={}, traceId={}",
                                    path, required, traceId);
                            return chain.filter(exchange);
                        }
                        log.warn("权限判定不可用，fail-closed 拒绝: path={}, perm={}, traceId={}",
                                path, required, traceId);
                        return deny(exchange, "权限校验服务不可用，已拒绝该操作", traceId, HttpStatus.FORBIDDEN);
                    }
                    if (verdict.configured && !verdict.allowed(required)) {
                        log.warn("接口权限不足: path={}, perm={}, traceId={}", path, required, traceId);
                        return deny(exchange, "当前账号无权限执行此操作", traceId, HttpStatus.FORBIDDEN);
                    }
                    return chain.filter(exchange);
                });
    }

    /** 命中规则则返回权限点短码，否则 null。 */
    private String matchPerm(KbGatewayProperties.Authz authz, String relativePath) {
        if (authz.getRules() == null) {
            return null;
        }
        for (KbGatewayProperties.Rule rule : authz.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getPattern()) || !StringUtils.hasText(rule.getPerm())) {
                continue;
            }
            if (PATH_MATCHER.match(rule.getPattern(), relativePath)) {
                return rule.getPerm();
            }
        }
        return null;
    }

    /** 拉取并判定（带缓存）。返回 null 表示「无法判定」，交由 fail-open/fail-closed 决定。 */
    private Mono<Verdict> resolveVerdict(KbGatewayProperties.Authz authz, String token) {
        String key = fingerprint(token);
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expireAt > now) {
            return Mono.just(hit.verdict);
        }
        return Mono.fromCallable(() -> fetchVerdict(authz, token))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(v -> {
                    if (v != null) {
                        // 轻量清理：条目过多时清过期项，避免无界增长
                        if (cache.size() > 2048) {
                            cache.entrySet().removeIf(e -> e.getValue().expireAt <= System.currentTimeMillis());
                        }
                        cache.put(key, new CacheEntry(now + authz.getCacheTtlMs(), v));
                    }
                });
    }

    /** 以「用户本人 token」向 auth-center 查该用户在 marschat-kbweb 的权限点。 */
    private Verdict fetchVerdict(KbGatewayProperties.Authz authz, String token) {
        try {
            String url = authz.getIssuer().replaceAll("/+$", "")
                    + "/auth/permissions?client=" + authz.getClientId();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("拉取中心权限失败 http={} client={}", resp.statusCode(), authz.getClientId());
                return null;
            }
            JsonNode body = objectMapper.readTree(resp.body());
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

    private String fullCode(String clientId, String perm) {
        return perm.startsWith(clientId + ":") ? perm : clientId + ":" + perm;
    }

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

    private String extractToken(ServerHttpRequest request) {
        String headerName = gatewayProperties.getJwt().getHeader();
        String header = request.getHeaders().getFirst(headerName);
        if (StringUtils.hasText(header) && header.startsWith(JwtAuthFilter.BEARER_PREFIX)) {
            return header.substring(JwtAuthFilter.BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private Mono<Void> deny(ServerWebExchange exchange, String message, String traceId, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(traceId)) {
            response.getHeaders().add(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
        Result<?> result = Result.fail(status.value(), message).withTraceId(traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private record CacheEntry(long expireAt, Verdict verdict) {
    }

    /** 一次判定的快照。 */
    private static final class Verdict {
        boolean configured;
        final Set<String> permissions = new java.util.LinkedHashSet<>();
        final Set<String> platformRoles = new java.util.LinkedHashSet<>();
        String username = "";

        boolean allowed(String fullCode) {
            if (!configured) {
                return true; // R10：未配置 = 行为不变
            }
            if (platformRoles.contains("admin") || platformRoles.contains("superadmin")) {
                return true;
            }
            return permissions.contains(fullCode);
        }
    }
}
