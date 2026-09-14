package com.kb.portal.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * portal 自有会话 token（HS256）工具。
 *
 * <p><b>🔴 密钥分离（2026-09-15 安全修复 · P0）</b>：此前 portal 与 infra-monitor 共用同一把
 * HS256 密钥（{@code PortalJwtSecretKey2026!MustBe32Bytes!!}，同时硬编码在本类的 {@code @Value}
 * 默认值、infra 的 compose 与 infra 的 JwtUtil 注释里），且 portal 的 {@code JwtInterceptor}
 * 只验签名不验签发方 —— 于是 <b>infra-monitor 签发的 token 能被 portal 验过</b>，跨应用横向越权成立。
 *
 * <p>修复三件事：
 * <ol>
 *   <li><b>代码里不留任何可用默认密钥</b>：{@code portal.jwt.secret} 无默认值，缺失/过短/命中已泄露值
 *       → 启动即抛 {@link IllegalStateException}（fail-fast，绝不带病上线）；</li>
 *   <li><b>密钥只从环境变量注入</b>（compose {@code PORTAL_JWT_SECRET}），与 infra 的
 *       {@code JWT_SECRET} 各自独立、绝不共享；</li>
 *   <li><b>token 带签发方声明</b>：签发时写入 {@code iss}（默认 {@code marschat-portal}），
 *       {@code JwtInterceptor} 验签后<b>必须再校验 iss</b>（见 {@link #isIssuedByPortal(String)}）。</li>
 * </ol>
 *
 * <p>⚠️ 轮换影响：密钥更换后<b>所有已登录用户的 portal_token 立即失效，需重新登录</b>（预期行为，
 * 已列入回归用例）。
 */
@Slf4j
@Component
public class JwtUtil {

    /** 已泄露的历史密钥（portal 与 infra-monitor 共用），命中即拒绝启动。 */
    private static final String REVOKED_SECRET = "PortalJwtSecretKey2026!MustBe32Bytes!!";

    /** HS256 密钥最小字节数（256 bit）。 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 签发方声明名（iss）。 */
    private static final String CLAIM_ISS = "iss";

    /** token 类型声明名（自定义，辅助判归属）。 */
    private static final String CLAIM_TYP = "typ";

    private static final String TOKEN_TYP = "portal";

    private final JWTSigner signer;
    private final long expireTime;
    private final String issuer;

    /**
     * @param secret      HS256 密钥，来自 {@code portal.jwt.secret}（环境变量 PORTAL_JWT_SECRET），无默认值
     * @param issuer      签发方标识，写入 token 的 {@code iss} 声明并在校验时比对
     * @param expireHours token 有效期（小时）
     */
    public JwtUtil(@Value("${portal.jwt.secret}") String secret,
                   @Value("${portal.jwt.issuer:marschat-portal}") String issuer,
                   @Value("${portal.jwt.expire-hours:24}") int expireHours) {
        validateSecret(secret);
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signer = JWTSignerUtil.hs256(keyBytes);
        this.expireTime = expireHours * 3600 * 1000L;
        this.issuer = issuer == null || issuer.isBlank() ? "marschat-portal" : issuer;
    }

    /**
     * 密钥强度与撤销校验（fail-fast）。
     *
     * <p>宁可启动失败，也不能用一把已知泄露/过短的密钥签发会话 token。
     *
     * @throws IllegalStateException 密钥缺失、过短或命中已撤销值
     */
    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "portal.jwt.secret 未配置：请通过环境变量 PORTAL_JWT_SECRET 注入（>=32 字节随机值），"
                            + "portal 与 infra-monitor 必须使用各自独立的密钥");
        }
        if (REVOKED_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "portal.jwt.secret 仍是已泄露的历史共享密钥（与 infra-monitor 共用），已拒绝启动；"
                            + "请为 portal 生成独立的 >=32 字节随机密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "portal.jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，HS256 要求密钥 >= 256 bit");
        }
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null);
    }

    public String generateToken(Long userId, String username, String role) {
        JWT jwt = JWT.create()
                .setPayload("userId", userId)
                .setPayload("username", username)
                // 🔴 归属声明：portal 的 JwtInterceptor 据此拒绝其他应用签发的 token
                .setPayload(CLAIM_ISS, issuer)
                .setPayload(CLAIM_TYP, TOKEN_TYP);
        if (role != null) {
            jwt.setPayload("role", role);
        }
        return jwt
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

    /**
     * 校验 token 是否由<b>本应用</b>签发（{@code iss} 与 {@code typ} 声明比对）。
     *
     * <p>签名正确但 {@code iss} 不匹配 = 别的系统拿同一把（或已泄露的）密钥签的 token，
     * 或本应用轮换前签发的老 token —— 一律拒绝。
     *
     * @return true = 归属 portal，可放行；false = 必须 401
     */
    public boolean isIssuedByPortal(String token) {
        JWT jwt = parseToken(token);
        if (jwt == null) {
            return false;
        }
        Object iss = jwt.getPayload(CLAIM_ISS);
        Object typ = jwt.getPayload(CLAIM_TYP);
        return iss != null && issuer.equals(iss.toString())
                && typ != null && TOKEN_TYP.equals(typ.toString());
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

    public String getRole(String token) {
        JWT jwt = parseToken(token);
        if (jwt != null) {
            Object role = jwt.getPayload("role");
            return role != null ? role.toString() : "user";
        }
        return null;
    }

    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }
}
