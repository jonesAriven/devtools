package com.jones.activation.util;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * auth-center OIDC RS256 验签器（2026-09-07 activecode 接入统一认证）。
 * 与 infra-monitor / kb-gateway 同口径：
 * - JWKS 拉取 + 内存缓存 TTL 10 分钟，启动预热失败不阻断（首个请求重试）；
 * - **SAS 的 JWKS 不带 alg 字段**（仅 kty=RSA/use=sig/kid/n/e），按 kty=RSA 收取；
 * - 验签校验签名 + issuer + 过期；kid 缺失回退单 key。
 *
 * 仅依赖 nimbus-jose-jwt（RS256 验签 + 声明解析一体），不引入额外 JWT 库。
 */
@Component
public class OidcTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenVerifier.class);
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

    /** RS256 验签；成功返回 claims（已校验 issuer 与过期），失败返回 null */
    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT signed = SignedJWT.parse(token);
            String kid = signed.getHeader().getKeyID();
            RSAPublicKey key = resolveKey(kid);
            if (key == null) {
                log.warn("OIDC RS256 无可用公钥: kid={}, cachedKeys={}", kid, keysByKid.keySet());
                return null;
            }
            JWSVerifier verifier = new RSASSAVerifier(key);
            if (!signed.verify(verifier)) {
                log.warn("OIDC RS256 签名校验失败: kid={}", kid);
                return null;
            }
            JWTClaimsSet claims = signed.getJWTClaimsSet();
            if (claims.getIssuer() == null || !claims.getIssuer().equals(issuer)) {
                log.warn("OIDC issuer 不匹配: expected={}, actual={}", issuer, claims.getIssuer());
                return null;
            }
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new java.util.Date())) {
                log.warn("OIDC token 已过期");
                return null;
            }
            return claims;
        } catch (Exception e) {
            log.warn("OIDC RS256 验签异常: {}", e.toString());
            return null;
        }
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
        List<JWK> jwks = jwkSet.getKeys();
        if (jwks == null || jwks.isEmpty()) {
            throw new IllegalStateException("JWKS 为空");
        }
        Map<String, RSAPublicKey> map = new HashMap<>();
        for (JWK jwk : jwks) {
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
