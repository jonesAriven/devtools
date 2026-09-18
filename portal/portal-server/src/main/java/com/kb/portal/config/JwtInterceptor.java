package com.kb.portal.config;

import cn.hutool.jwt.JWT;
import com.kb.portal.util.JwtUtil;
import com.kb.portal.util.JwtUtil.RejectReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * portal 会话校验拦截器。
 *
 * <p><b>🔴 2026-09-15 安全修复（P0）：补「token 归属校验」。</b>
 * 此前本拦截器<b>只验签名</b>（{@link JwtUtil#validateToken}）：只要能用 portal 的 HS256 密钥验过就放行，
 * 既不校验 token 是哪家的签发方，也不校验 role 以外的声明。而 portal 与 infra-monitor 当时共用同一把
 * 硬编码密钥 —— <b>infra-monitor 签发的 token 可以直接调 portal 的管理接口</b>（跨应用横向越权）。
 *
 * <p>修复：密钥分离（见 {@link JwtUtil}）之外，这里在验签通过后<b>必须再校验 {@code iss}/{@code typ}
 * 归属声明</b>（{@link JwtUtil#isIssuedByPortal(String)}），非 portal 签发的 token 一律 401。
 * role 注入逻辑保持不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");
        // token 提升到外层作用域：下面「未登录或登录已过期」分支要拿它采样（可为 null）
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                // 🔴 验签通过 ≠ 可放行：必须是本应用签发的 token
                if (!jwtUtil.isIssuedByPortal(token)) {
                    // D-2/E0：原 WARN **只有 uri 一个实参** ⇒ 永远看不到任何 token 信息。
                    // 这里补 iss/typ/alg + sha256 指纹，并用**实际解析值**替掉原来那句固定猜测文案
                    // （"疑似其他应用签发"是写死的，不反映真实 iss/typ）。判定逻辑未变。
                    logReject(uri, true, token, jwtUtil.rejectReasonAfterVerify(token));
                    writeUnauthorized(response, "token 签发方不合法，请重新登录");
                    return false;
                }
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String role = jwtUtil.getRole(token);
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
                request.setAttribute("role", role == null ? "user" : role);
                return true;
            }
        }

        // D-2/E0：**真缺口** —— 这条分支（无 Bearer / 验签失败 / 已过期）原本**完全不打日志**，
        // 于是「线上到底有没有 401、为什么 401」无法断言；只补上面那条是远远不够的。
        // 注意：此处 `token == null` 覆盖「无 Authorization」与「有头但不是 Bearer」两种情形。
        logReject(uri, token != null, token, jwtUtil.rejectReasonWhenInvalid(token));
        writeUnauthorized(response, "未登录或登录已过期");
        return false;
    }

    // ==========================================================================
    // D-2 / E0（2026-09-18）：拒绝路径采样日志 —— **只打日志，零行为变更**
    //
    // 授权边界（硬约束，不可越界）：
    //   · 允许记录：uri / 有无 Authorization / 实际 iss·typ·alg / 失败原因 / sha256(token) 前 8 位
    //   · **严禁记录**：原始 token、任何可读前缀（一律以 sha256 指纹替代）
    //   · 级别 WARN，**仅拒绝时**打印（禁止逐请求 INFO，防日志暴涨）
    // ==========================================================================

    /** 采样日志事件名（固定，便于告警聚合） */
    private static final String REJECT_EVENT = "portal_jwt_reject";

    /**
     * 拒绝时落一条结构化 WARN（两个 401 发射点都走它）。
     *
     * <p>⚠️ 所有取值都包在 try/catch 里：**日志绝不能成为新的 500 来源**；
     * 取不到就输出 {@code null}，绝不臆造。
     */
    private void logReject(String uri, boolean hasAuth, String token, RejectReason reason) {
        String iss = null;
        String typ = null;
        String alg = null;
        try {
            JWT jwt = jwtUtil.decodeUnverified(token);
            if (jwt != null) {
                Object issObj = jwt.getPayload(JwtUtil.CLAIM_ISS);
                if (issObj != null) {
                    iss = issObj.toString();
                }
                Object typObj = jwt.getPayload(JwtUtil.CLAIM_TYP);
                if (typObj != null) {
                    typ = typObj.toString();
                }
                alg = jwt.getAlgorithm();
            }
        } catch (Exception ignored) {
            // 解码失败一律保持 null，绝不影响后续拒绝动作
        }
        log.warn("{} reason={} uri={} has_auth={} iss={} typ={} alg={} token_fp={}",
                REJECT_EVENT, reason, uri, hasAuth, iss, typ, alg, tokenFingerprint(token));
    }

    /**
     * token 的**不可逆**指纹：{@code sha256(token)} 取前 8 位十六进制。
     *
     * <p>作用：既能把「客户端抓到的 token」与「服务端拒绝的 token」对上，
     * 又**不泄露凭证本体**（严禁记录原始 token 或任何可读前缀）。
     */
    private String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {   // 4 字节 = 8 位十六进制
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
