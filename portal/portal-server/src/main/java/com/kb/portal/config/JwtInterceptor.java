package com.kb.portal.config;

import com.kb.portal.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                // 🔴 验签通过 ≠ 可放行：必须是本应用签发的 token
                if (!jwtUtil.isIssuedByPortal(token)) {
                    log.warn("拒绝跨应用 token：uri={} 签名可验但签发方/类型不匹配（疑似其他应用签发或轮换前的老 token）",
                            request.getRequestURI());
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

        writeUnauthorized(response, "未登录或登录已过期");
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
