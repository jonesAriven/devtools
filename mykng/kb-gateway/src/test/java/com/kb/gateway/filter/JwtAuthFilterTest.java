package com.kb.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.gateway.config.KbGatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtAuthFilter 单元测试。
 * <p>
 * 使用 Spring WebFlux 测试工具 {@link MockServerHttpRequest} / {@link MockServerWebExchange}
 * 构造 Reactive 请求上下文，配合 Mockito 模拟 {@link GatewayFilterChain}，
 * 不依赖任何外部环境（无需启动下游服务 / Spring 容器）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JWT 鉴权过滤器单元测试")
class JwtAuthFilterTest {

    private static final String SECRET =
            "YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!";

    @Mock
    private GatewayFilterChain chain;

    private KbGatewayProperties properties;
    private JwtAuthFilter jwtAuthFilter;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        properties = new KbGatewayProperties();
        properties.setContextPath("/kb");
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setHeader("Authorization");
        properties.setWhitelist(new ArrayList<>(Arrays.asList(
                "/kb/api/auth/login",
                "/kb/api/auth/refresh",
                "/kb/api/share/verify/**",
                "/kb/api/share/detail/**"
        )));
        jwtAuthFilter = new JwtAuthFilter(new ObjectMapper(), properties);
        // 手动触发 @PostConstruct 逻辑（单元测试不会被 Spring 调用）
        jwtAuthFilter.init();
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String accessToken(String subject, String username, long expirationMillis) {
        return Jwts.builder()
                .subject(subject)
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /** 白名单路径直接放行 - 无需 token */
    @Test
    @DisplayName("白名单路径直接放行 - 无需 token")
    void whitelistPath_passesWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/kb/api/auth/login")
                .header("Content-Type", "application/json")
                .body("{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain).filter(any(ServerWebExchange.class));
    }

    /** 受保护路径无 token → 401 */
    @Test
    @DisplayName("受保护路径无 token → 返回 401")
    void protectedPathWithoutToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    /** 受保护路径无效 token → 401 */
    @Test
    @DisplayName("受保护路径无效 token → 返回 401")
    void protectedPathWithInvalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .header("Authorization", "Bearer invalid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    /** 受保护路径有效 access token → 通过验证并注入 X-User-Id / X-Username 头 */
    @Test
    @DisplayName("受保护路径有效 access token → 通过验证并注入 X-User-Id / X-Username 头")
    void protectedPathWithValidToken_passesAndInjectsHeaders() {
        String token = accessToken("123", "alice", 60_000);
        // 客户端伪造的同名头应被覆盖（防越权）
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .header("Authorization", "Bearer " + token)
                .header(JwtAuthFilter.HEADER_USER_ID, "forged-user-id")
                .header(JwtAuthFilter.HEADER_USERNAME, "forged-username")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange passedExchange = captor.getValue();

        // 注入真实用户信息（覆盖客户端伪造值）
        assertEquals("123", passedExchange.getRequest().getHeaders().getFirst(JwtAuthFilter.HEADER_USER_ID));
        assertEquals("alice", passedExchange.getRequest().getHeaders().getFirst(JwtAuthFilter.HEADER_USERNAME));
    }

    /** 受保护路径过期 token → 401 */
    @Test
    @DisplayName("受保护路径过期 token → 返回 401")
    void protectedPathWithExpiredToken_returns401() {
        String expiredToken = Jwts.builder()
                .subject("123")
                .claim("username", "alice")
                .claim("type", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .header("Authorization", "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    /** refresh 类型 token 不能用于访问 API → 401 */
    @Test
    @DisplayName("refresh 类型 token 不能访问受保护接口 → 返回 401")
    void refreshTokenRejected_returns401() {
        String refresh = Jwts.builder()
                .subject("123")
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .header("Authorization", "Bearer " + refresh)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNotNull(exchange.getResponse().getStatusCode());
    }
}
