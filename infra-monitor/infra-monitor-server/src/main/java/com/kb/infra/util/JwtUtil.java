package com.kb.infra.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;
    private final OidcTokenVerifier oidcTokenVerifier;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration,
                   OidcTokenVerifier oidcTokenVerifier) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.oidcTokenVerifier = oidcTokenVerifier;
    }

    public String generate(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public String parseUsername(String token) {
        // 双验签（2026-09-07 统一认证接入）：先 legacy HS256（历史自签 access token），
        // 失败回退 auth-center RS256（OIDC token，claims: uid/username/realm/role，sub=用户主键）
        Claims claims = tryParseHs256(token);
        if (claims == null) {
            claims = oidcTokenVerifier.verify(token);
        }
        if (claims == null) {
            return null;
        }
        // 两种 token 均有 username claim（legacy 与 OIDC 的 tokenCustomizer 同口径注入）
        String username = claims.get("username", String.class);
        return username != null ? username : claims.getSubject();
    }

    /** legacy HS256 本地验签；失败返回 null（交由 RS256 链路继续） */
    private Claims tryParseHs256(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        return parseUsername(token) != null;
    }
}
