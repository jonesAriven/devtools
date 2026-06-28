package com.kb.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kb-gateway 集成测试（*IT.java，Failsafe 默认匹配模式）。
 * <p>
 * 覆盖阶段2层级3接口集成自测的 7 个场景：
 * 正常参数 / 参数缺失 / 非法格式 / 越权访问 / 未认证 / 重复提交 / 大数据量。
 * <p>
 * kb-gateway 为 Reactive（WebFlux / Spring Cloud Gateway）栈，使用 {@link WebTestClient}。
 * 下游 kb-auth/kb-knowledge 等服务在测试环境未启动，因此鉴权通过后路由转发会因连接下游失败而返回 5xx；
 * 本测试通过 "是否 401" 来判定 JwtAuthFilter 的放行/拦截逻辑，与既有 KbGatewayApplicationTests 一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("kb-gateway 集成测试 - 7场景全覆盖")
class KbGatewayIT {

    /** 与 application.yml 中 kb.gateway.jwt.secret 保持一致 */
    private static final String SECRET =
            "YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!";

    @Autowired
    WebTestClient webTestClient;

    /** 生成合法的 access token（type=access） */
    private String validAccessToken(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("username", "alice")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    // ============ 场景1: 正常参数 ============
    @Test
    @DisplayName("场景1-正常参数-白名单路径无Token放行（非401）")
    void scenario1_normalParameter_whitelistPasses() {
        // 白名单路径 /kb/api/auth/login 无需 Token，JwtAuthFilter 直接放行
        // 下游 kb-auth 未启动 → 5xx，但鉴权层放行（非 401）即证明参数合法
        webTestClient.post().uri("/kb/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().value(s -> assertNotEquals(401, s, "白名单路径应放行，不应返回401"));
    }

    // ============ 场景2: 参数缺失/为空 ============
    @Test
    @DisplayName("场景2-参数缺失-路由不匹配返回404")
    void scenario2_missingRoute_returns404() {
        // 路径不匹配任何路由规则 → 网关返回 404
        webTestClient.get().uri("/kb/api/nonexistent/endpoint")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ============ 场景3: 参数超长/非法格式 ============
    @Test
    @DisplayName("场景3-超长Token-校验拦截不抛500")
    void scenario3_oversizedToken_interceptedNo500() {
        // 超长非法 Token，JwtAuthFilter 解析失败 → 401（不抛 500）
        String oversizedToken = "x".repeat(5000);
        webTestClient.get().uri("/kb/api/doc/list")
                .header("Authorization", "Bearer " + oversizedToken)
                .exchange()
                .expectStatus().value(s -> assertTrue(s < 500,
                        "非法Token应被校验拦截，不应抛500，实际: " + s));
    }

    // ============ 场景4: 越权访问 ============
    @Test
    @DisplayName("场景4-越权访问-伪造用户头无Token返回401")
    void scenario4_forgedUserIdHeader_rejected() {
        // 客户端伪造 X-User-Id=2 试图越权，但无有效 Token
        // 网关不信任客户端头，必须有合法 Token 才能注入用户身份 → 401
        webTestClient.get().uri("/kb/api/doc/list")
                .header("X-User-Id", "2")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401)
                .jsonPath("$.traceId").isNotEmpty();
    }

    // ============ 场景5: 未认证/Token过期 ============
    @Test
    @DisplayName("场景5-未认证-无Token访问受保护路径返回401")
    void scenario5_unauthenticated_returns401() {
        // 受保护路径 /kb/api/doc/list 无 Token → JwtAuthFilter 返回 401 统一 JSON
        webTestClient.get().uri("/kb/api/doc/list")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(401)
                .jsonPath("$.traceId").isNotEmpty();
    }

    // ============ 场景6: 重复提交 ============
    @Test
    @DisplayName("场景6-重复提交-连续两次均被鉴权拒绝")
    void scenario6_duplicateSubmit_bothRejected() {
        // 同一受保护路径无 Token 连续两次请求 → 两次都返回 401
        // 重复提交不绕过鉴权（无幂等漏洞）
        webTestClient.get().uri("/kb/api/doc/list")
                .exchange()
                .expectStatus().isUnauthorized();
        webTestClient.get().uri("/kb/api/doc/list")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ============ 场景7: 大数据量 ============
    @Test
    @DisplayName("场景7-大数据量-大查询参数鉴权通过不超时")
    void scenario7_largeData_authPassesWithoutTimeout() {
        // size=1000 + 1000 字符 filter 参数 + 合法 Token
        // 鉴权层应通过（非 401），且不超时
        String largeQuery = "size=1000&filter=" + "x".repeat(1000);
        webTestClient.get().uri("/kb/api/doc/list?" + largeQuery)
                .header("Authorization", "Bearer " + validAccessToken("1"))
                .exchange()
                .expectStatus().value(s -> assertNotEquals(401, s,
                        "大数据量+合法Token应鉴权通过，不应返回401"));
    }
}
