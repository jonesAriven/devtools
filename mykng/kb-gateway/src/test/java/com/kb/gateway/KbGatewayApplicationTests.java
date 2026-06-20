package com.kb.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 网关鉴权与白名单行为测试（Reactive WebTestClient，下游服务未启动）。
 * <p>
 * 下游 kb-auth/kb-knowledge 等未运行，因此路由命中的请求会在鉴权通过后因连接下游失败而返回 5xx；
 * 本测试通过 "是否 401" 来判定 JwtAuthFilter 的放行/拦截逻辑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KbGatewayApplicationTests {

    private static final String SECRET =
            "YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!";

    @Autowired
    WebTestClient webTestClient;

    private String validAccessToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("123")
                .claim("username", "alice")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    /** 受保护路径无 token → 401 统一 JSON */
    @Test
    void protectedPathWithoutToken_returns401() {
        webTestClient.get().uri("/kb/api/doc/list")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401)
                .jsonPath("$.traceId").isNotEmpty();
    }

    /** 受保护路径 token 非法 → 401 */
    @Test
    void protectedPathWithInvalidToken_returns401() {
        webTestClient.get().uri("/kb/api/doc/list")
                .header("Authorization", "Bearer invalid.token.here")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /** refresh 类型 token 不能用于访问 API → 401 */
    @Test
    void refreshTokenRejected_returns401() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String refresh = Jwts.builder()
                .subject("123").claim("type", "refresh")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact();
        webTestClient.get().uri("/kb/api/doc/list")
                .header("Authorization", "Bearer " + refresh)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /** 受保护路径携带合法 access token → 鉴权通过（下游未启动 → 5xx，非 401） */
    @Test
    void protectedPathWithValidToken_passesAuth() {
        webTestClient.get().uri("/kb/api/doc/list")
                .header("Authorization", "Bearer " + validAccessToken())
                .exchange()
                .expectStatus().value(s -> org.junit.jupiter.api.Assertions.assertNotEquals(401, s));
    }

    /** 白名单路径无 token → 鉴权放行（下游未启动 → 5xx，非 401） */
    @Test
    void whitelistPathWithoutToken_passesAuth() {
        webTestClient.post().uri("/kb/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().value(s -> org.junit.jupiter.api.Assertions.assertNotEquals(401, s));
    }

    /** 白名单分享校验路径无 token → 放行 */
    @Test
    void shareVerifyWhitelist_passesAuth() {
        webTestClient.get().uri("/kb/api/share/verify/abc123")
                .exchange()
                .expectStatus().value(s -> org.junit.jupiter.api.Assertions.assertNotEquals(401, s));
    }
}
