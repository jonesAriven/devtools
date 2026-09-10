package com.kb.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marschat.common.result.Result;
import com.kb.gateway.config.KbGatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 鉴权过滤器（Reactive GlobalFilter）。
 * <p>
 * 流程：
 * <ol>
 *   <li>放行 OPTIONS 预检、白名单路径、非受管路径；</li>
 *   <li>从 {@code Authorization: Bearer xxx} 提取 access token；</li>
 *   <li>用 jjwt 本地验签 + 校验过期 + 校验 type=access（不回调 auth-center，保证高性能）；</li>
 *   <li>校验通过：移除客户端伪造的 {@code X-User-Id}，注入真实的 userId / username 到下游；</li>
 *   <li>校验失败：返回 401 统一 JSON（携带 traceId）。</li>
 * </ol>
 * Authorization 头保留转发，便于 auth-center 自身的 Spring Security 对其受保护端点二次校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String CLAIM_UID = "uid";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ObjectMapper objectMapper;
    private final KbGatewayProperties gatewayProperties;
    private final OidcTokenVerifier oidcTokenVerifier;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(
                gatewayProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        // 在 TraceIdFilter 之后执行，便于错误响应携带 traceId
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 放行 CORS 预检
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 2. 放行白名单
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 3. 仅对网关受管路径鉴权（其余路径交由路由 404）
        String contextPath = gatewayProperties.getContextPath();
        if (contextPath == null) contextPath = "/kb";
        String apiPrefix = contextPath + "/api/";
        if (!path.startsWith(apiPrefix)) {
            return chain.filter(exchange);
        }

        String traceId = request.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);

        // 4. 提取 token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "未登录或缺少访问令牌", traceId);
        }

        // 5. 双验签：先 legacy HS256（type=access），失败回退 auth-center RS256（OIDC，issuer+exp 校验）
        Claims claims = tryParseHs256(token, path, traceId);
        boolean legacy = claims != null;
        if (claims == null) {
            claims = oidcTokenVerifier.verify(token);
        }
        if (claims == null) {
            log.warn("JWT 校验失败(HS256+RS256 均未通过): traceId={}, path={}", traceId, path);
            return unauthorized(exchange, "访问令牌无效或已过期", traceId);
        }

        // 6. 仅 legacy token 要求 type=access（OIDC access token 无该 claim）
        if (legacy) {
            String tokenType = claims.get(CLAIM_TYPE, String.class);
            if (!TYPE_ACCESS.equals(tokenType)) {
                return unauthorized(exchange, "令牌类型错误，请使用访问令牌", traceId);
            }
        }

        // 7. 注入用户信息到下游（先清除客户端伪造的同名头，防越权）
        // legacy: sub=userId；OIDC: uid claim 为业务用户 id（缺省回退 sub）
        String userId = claims.getSubject();
        if (!legacy) {
            String uid = claims.get(CLAIM_UID, String.class);
            if (StringUtils.hasText(uid)) {
                userId = uid;
            }
        }
        String username = claims.get("username", String.class);
        final String finalUserId = userId;

        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USERNAME);
                    h.set(HEADER_USER_ID, finalUserId);
                    if (StringUtils.hasText(username)) {
                        h.set(HEADER_USERNAME, username);
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** legacy HS256 本地验签；失败返回 null（交由 RS256 链路继续） */
    private Claims tryParseHs256(String token, String path, String traceId) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isWhitelisted(String path) {
        List<String> whitelist = gatewayProperties.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String headerName = gatewayProperties.getJwt().getHeader();
        String header = request.getHeaders().getFirst(headerName);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message, String traceId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(traceId)) {
            response.getHeaders().add(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
        Result<?> result = Result.fail(401, message).withTraceId(traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("序列化网关错误响应失败: traceId={}", traceId, e);
            return Mono.error(e);
        }
    }
}
