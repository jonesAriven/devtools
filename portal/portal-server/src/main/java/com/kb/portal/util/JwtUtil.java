package com.kb.portal.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final JWTSigner signer;
    private final long expireTime;

    public JwtUtil(@Value("${portal.jwt.secret:PortalJwtSecretKey2026!}") String secret,
                   @Value("${portal.jwt.expire-hours:24}") int expireHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signer = JWTSignerUtil.hs256(keyBytes);
        this.expireTime = expireHours * 3600 * 1000L;
    }

    public String generateToken(Long userId, String username) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("username", username)
                .setExpiresAt(new Date(System.currentTimeMillis() + expireTime))
                .setIssuedAt(new Date())
                .sign(signer);
    }

    public JWT parseToken(String token) {
        try {
            if (JWTUtil.verify(token, signer)) {
                JWT jwt = JWTUtil.parseToken(token);
                Object expObj = jwt.getPayload("exp");
                if (expObj != null) {
                    long expTime = ((Number) expObj).longValue() * 1000L;
                    if (expTime > System.currentTimeMillis()) {
                        return jwt;
                    }
                } else {
                    return jwt;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Long getUserId(String token) {
        JWT jwt = parseToken(token);
        if (jwt != null) {
            Object userId = jwt.getPayload("userId");
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
        }
        return null;
    }

    public String getUsername(String token) {
        JWT jwt = parseToken(token);
        if (jwt != null) {
            Object username = jwt.getPayload("username");
            return username != null ? username.toString() : null;
        }
        return null;
    }

    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }
}
