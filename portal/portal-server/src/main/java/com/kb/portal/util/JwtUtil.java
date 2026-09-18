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
    public static final String CLAIM_ISS = "iss";

    /** token 类型声明名（自定义，辅助判归属）。 */
    public static final String CLAIM_TYP = "typ";

    /** 过期时间声明名（exp）。 */
    public static final String CLAIM_EXP = "exp";

    private static final String TOKEN_TYP = "portal";

    /**
     * 拒绝/失败原因分类（**仅用于采样日志聚合，不参与任何放行判定**）。
     *
     * <p>E0 采样日志据此把「只能看到 HTTP 401」升级为「服务端可断言的失败原因」。
     */
    public enum RejectReason {
        /** 请求根本没有携带 Bearer 头 */
        NO_BEARER,
        /** 验签失败，或 token 格式非法无法解码 */
        BAD_SIGNATURE,
        /** 签名/格式可解析，但 exp 已过期 */
        EXPIRED,
        /** 签名可验、未过期，但 iss 不是本应用 */
        ISSUER_MISMATCH,
        /** 签名可验、未过期、iss 正确，但 typ 缺失或不是 portal */
        TYPE_MISMATCH
    }

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

    // ==========================================================================
    // D-2 / E0（2026-09-18）：拒绝路径采样日志的**诊断支撑方法**
    //
    // ⚠️ 全部**只用于打日志**，**绝不参与任何放行/拒绝判定** —— 判定只能走
    //    validateToken / isIssuedByPortal。下面这些方法的返回值不可信（可伪造）。
    //
    // 背景：portal 的 401 有两个发射点，其中「未登录或登录已过期」那条**原本完全不打日志**，
    //       导致「线上到底有没有 401、为什么 401」无法断言 —— 这是本次要补的可观测性缺口。
    // ==========================================================================

    /**
     * 仅解码、**不验签**（用于拒绝时采样日志取 iss/typ/alg/exp）。
     *
     * <p>⚠️ <b>仅用于日志，绝不可用于鉴权判定</b>：它不校验签名，任何人都可以伪造 payload，
     * 因此返回值<b>不得</b>参与任何放行/拒绝决策。
     *
     * @param token JWT 字符串
     * @return 解码成功返回 JWT；null/空白/格式非法一律返回 null
     */
    public JWT decodeUnverified(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return JWTUtil.parseToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 诊断用：在「<b>签名已验过且未过期</b>」的前提下，判定归属校验（iss/typ）到底哪项不符。
     *
     * <p>调用点 = {@code JwtInterceptor} 中 {@code !isIssuedByPortal(token)} 成立时。
     *
     * @return {@link RejectReason#ISSUER_MISMATCH} 或 {@link RejectReason#TYPE_MISMATCH}
     */
    public RejectReason rejectReasonAfterVerify(String token) {
        JWT jwt = parseToken(token);
        if (jwt == null) {
            // 理论上不可达（调用前 validateToken 已通过），兜底不臆造
            return RejectReason.BAD_SIGNATURE;
        }
        Object iss = jwt.getPayload(CLAIM_ISS);
        Object typ = jwt.getPayload(CLAIM_TYP);
        if (iss == null || !issuer.equals(iss.toString())) {
            return RejectReason.ISSUER_MISMATCH;
        }
        if (typ == null || !TOKEN_TYP.equals(typ.toString())) {
            return RejectReason.TYPE_MISMATCH;
        }
        return RejectReason.BAD_SIGNATURE;
    }

    /**
     * 诊断用：{@link #validateToken} 已返回 false 时，区分「已过期」与「验签/格式失败」。
     *
     * <p>⚠️ {@code parseToken} 把「验签失败」和「已过期」<b>都归并成返回 null</b>，
     * 调用方无法区分 —— 所以要区分二者<b>必须</b>走 {@link #decodeUnverified} 这条只解码不验签的路径。
     *
     * @param token 可为 null（无 Bearer 头时）
     * @return {@link RejectReason#NO_BEARER} / {@link RejectReason#EXPIRED} / {@link RejectReason#BAD_SIGNATURE}
     */
    public RejectReason rejectReasonWhenInvalid(String token) {
        if (token == null || token.isBlank()) {
            return RejectReason.NO_BEARER;
        }
        JWT jwt = decodeUnverified(token);
        if (jwt == null) {
            // 连解码都失败 ⇒ 格式非法，按签名/格式问题计
            return RejectReason.BAD_SIGNATURE;
        }
        Object expObj = jwt.getPayload(CLAIM_EXP);
        if (expObj != null) {
            try {
                long expTime = ((Number) expObj).longValue() * 1000L;
                if (expTime <= System.currentTimeMillis()) {
                    return RejectReason.EXPIRED;
                }
            } catch (Exception ignored) {
                // exp 不是数字 ⇒ 无法判定，落到 BAD_SIGNATURE
            }
        }
        return RejectReason.BAD_SIGNATURE;
    }
}
