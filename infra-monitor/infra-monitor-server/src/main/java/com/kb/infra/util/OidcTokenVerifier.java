package com.kb.infra.util;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * auth-center OIDC RS256 验签器（2026-09-07 infra-monitor 接入统一认证）。
 * 与 kb-gateway / auth-core 2.0 同口径：
 * - JWKS 拉取 + 内存缓存 TTL 10 分钟，启动预热失败不阻断（首个请求重试）；
 * - **SAS 的 JWKS 不带 alg 字段**（仅 kty=RSA/use=sig/kid/n/e），按 kty=RSA 收取；
 * - 验签校验 issuer + 过期；kid 缺失回退单 key。
 */
@Slf4j
@Component
public class OidcTokenVerifier {

    private static final long JWKS_TTL_MS = Duration.ofMinutes(10).toMillis();

    @Value("${jwt.oidc-issuer:https://auth.marschat.online}")
    private String issuer;

    @Value("${jwt.oidc-jwks-uri:https://auth.marschat.online/oauth2/jwks}")
    private String jwksUri;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Map<String, RSAPublicKey> keysByKid = Map.of();
    private volatile long fetchedAt = 0;

    @PostConstruct
    public void warmUp() {
        try {
            refreshJwks();
            log.info("OIDC JWKS 预热完成: issuer={}, keys={}", issuer, keysByKid.size());
        } catch (Exception e) {
            log.warn("OIDC JWKS 预热失败（将随首个 OIDC 请求重试）: {}", e.toString());
        }
    }

    /** RS256 验签；成功返回 claims，失败返回 null */
    public Claims verify(String token) {
        try {
            String kid = extractKid(token);
            RSAPublicKey key = resolveKey(kid);
            if (key == null) {
                log.warn("OIDC RS256 无可用公钥: kid={}, cachedKeys={}", kid, keysByKid.keySet());
                return null;
            }
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("OIDC RS256 验签异常: {}", e.toString());
            return null;
        }
    }

    private String extractKid(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        byte[] header = Base64.getUrlDecoder().decode(parts[0]);
        String json = new String(header, java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"kid\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private RSAPublicKey resolveKey(String kid) throws Exception {
        Map<String, RSAPublicKey> keys = keysByKid;
        boolean expired = System.currentTimeMillis() - fetchedAt > JWKS_TTL_MS;
        if (keys.isEmpty() || expired) {
            lock.writeLock().lock();
            try {
                if (keysByKid.isEmpty() || System.currentTimeMillis() - fetchedAt > JWKS_TTL_MS) {
                    refreshJwks();
                }
                keys = keysByKid;
            } finally {
                lock.writeLock().unlock();
            }
        }
        if (kid != null && keys.containsKey(kid)) {
            return keys.get(kid);
        }
        return keys.size() == 1 ? keys.values().iterator().next() : null;
    }

    private synchronized void refreshJwks() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUri))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("JWKS 拉取失败 HTTP " + response.statusCode());
        }
        JWKSet jwkSet = JWKSet.parse(response.body());
        List<com.nimbusds.jose.jwk.JWK> jwks = jwkSet.getKeys();
        if (jwks == null || jwks.isEmpty()) {
            throw new IllegalStateException("JWKS 为空");
        }
        Map<String, RSAPublicKey> map = new HashMap<>();
        for (com.nimbusds.jose.jwk.JWK jwk : jwks) {
            // SAS 的 JWKS 不带 alg 字段，按 kty=RSA 收取
            if (jwk instanceof RSAKey rsaKey) {
                map.put(rsaKey.getKeyID(), rsaKey.toRSAPublicKey());
            }
        }
        if (map.isEmpty()) {
            throw new IllegalStateException("JWKS 中无 RSA key（keys=" + jwks.size() + "）");
        }
        this.keysByKid = Map.copyOf(map);
        this.fetchedAt = System.currentTimeMillis();
    }
}
