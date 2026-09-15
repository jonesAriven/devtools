package com.kb.infra.util;

import com.marschat.auth.oidc.OidcTokenVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 本应用 JWT 工具（HS256 自签 + auth-center OIDC RS256 双验签）。
 *
 * <p>⚠️ 2026-09-14（统一鉴权收敛）：RS256 验签器改为直接复用公共库
 * {@link com.marschat.auth.oidc.OidcTokenVerifier}（auth-core），本应用原先的
 * {@code com.kb.infra.util.OidcTokenVerifier} 副本已删除 —— 与 kb-gateway / kb-ops 同源实现，
 * 避免"同一验签逻辑四处各抄一份、改一处忘三处"。
 * 该 bean 由 {@code config/OidcConfig} 手动声明（本应用显式排除 auth-core 的自动装配）。
 */
/**
 * 本应用 JWT 工具（HS256 自签 + auth-center OIDC RS256 双验签）。
 *
 * <p>⚠️ 2026-09-14（统一鉴权收敛）：RS256 验签器改为直接复用公共库
 * {@link com.marschat.auth.oidc.OidcTokenVerifier}（auth-core），本应用原先的
 * {@code com.kb.infra.util.OidcTokenVerifier} 副本已删除 —— 与 kb-gateway / kb-ops 同源实现，
 * 避免"同一验签逻辑四处各抄一份、改一处忘三处"。
 * 该 bean 由 {@code config/OidcConfig} 手动声明（本应用显式排除 auth-core 的自动装配）。
 *
 * <p><b>🔴 2026-09-15 安全修复（P0 密钥分离）</b>：{@code jwt.secret} 此前在 application.yml 里
 * 留有可用默认值，且与 portal 共用同一把密钥（跨应用 token 互认）。现改为<b>无默认值、只从环境变量
 * {@code JWT_SECRET} 注入</b>，缺失/过短 → 启动即抛 {@link IllegalStateException}（fail-fast）。
 *
 * <p>同时新增 {@link #parseLocalUsername(String)}（只验本应用 HS256）与
 * {@link #parseOidcUsername(String)}（只验中心 RS256），供 {@code InfraPermissionChecker}
 * 判定「请求者到底是谁」—— 两种 token 的处置策略不同（见其类注释）。
 */
@Component
public class JwtUtil {

    /** HS256 密钥最小字节数（256 bit）。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiration;
    private final OidcTokenVerifier oidcTokenVerifier;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration,
                   OidcTokenVerifier oidcTokenVerifier) {
        validateSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.oidcTokenVerifier = oidcTokenVerifier;
    }

    /** 密钥缺失/过短 → fail-fast，绝不带病上线（jwt.secret 已无默认值）。 */
    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置：请通过环境变量 JWT_SECRET 注入（>=32 字节随机值），"
                            + "infra-monitor 与 portal 必须使用各自独立的密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，HS256 要求密钥 >= 256 bit");
        }
    }

    public String generate(String username) {
        return generate(username, null);
    }

    /**
     * 签发本应用自有 token，并写入 {@code role} 声明。
     *
     * <p>2026-09-15（Phase 11）：账密/邮箱码登录统一走认证中心后，身份角色只能从中心响应取得
     * （中心 admin 的 role 为 {@code superadmin}）。前端 {@code MainLayout.vue} 以 token 的
     * {@code role} 声明做「用户管理」菜单兜底过滤，缺该声明会导致菜单被误隐藏，故须原样带入。
     *
     * @param role 中心返回的角色原值；为空/null 时不写该声明（等价于旧 {@link #generate(String)}）
     */
    public String generate(String username, String role) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration));
        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        return builder.signWith(key).compact();
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

    /**
     * <b>只</b>按本应用 HS256 密钥解析（/auth/login 与邮箱码登录签发的自有会话 token）。
     *
     * <p>与 {@link #parseUsername(String)} 的差别：不做 OIDC 回退。鉴权时要据此区分
     * 「本应用本地会话」与「中心 OIDC 身份」，两者权限判定路径不同。
     *
     * @return 本应用自签 token 的用户名；非本应用签发返回 {@code null}
     */
    public String parseLocalUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Claims claims = tryParseHs256(token);
        if (claims == null) {
            return null;
        }
        String username = claims.get("username", String.class);
        return username != null ? username : claims.getSubject();
    }

    /**
     * <b>只</b>按 auth-center OIDC RS256 验签解析（统一登录用户持有的中心 access token）。
     *
     * @return 中心 token 的用户名；非 OIDC token 或验签失败返回 {@code null}
     */
    public String parseOidcUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = oidcTokenVerifier.verify(token);
            if (claims == null) {
                return null;
            }
            String username = claims.get("username", String.class);
            return username != null ? username : claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        return parseUsername(token) != null;
    }
}
