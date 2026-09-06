package com.kb.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.auth.entity.JwtBlacklist;
import com.kb.auth.mapper.JwtBlacklistMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final JwtBlacklistMapper jwtBlacklistMapper;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            if (isBlacklisted(token)) {
                sendUnauthorized(response, "Token已失效");
                return;
            }

            try {
                Long principalId;
                boolean legacyValid = jwtTokenProvider.validateToken(token)
                        && "access".equals(jwtTokenProvider.getTokenType(token));

                if (legacyValid) {
                    // legacy HS256 token（kb-auth 自签）
                    principalId = jwtTokenProvider.getUserIdFromToken(token);
                } else {
                    // OIDC RS256 token（auth-center 签发，claims: uid/username/role）
                    try {
                        var jwt = jwtDecoder.decode(token);
                        String uid = jwt.getClaimAsString("uid");
                        if (uid == null || uid.isBlank()) {
                            uid = jwt.getSubject();
                        }
                        principalId = Long.parseLong(uid);
                    } catch (Exception rsError) {
                        log.debug("RS256 验签失败: {}", rsError.getMessage());
                        sendUnauthorized(response, "Token无效或已过期");
                        return;
                    }
                }

                {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(principalId));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.warn("JWT认证失败: {}", e.getMessage());
                sendUnauthorized(response, "Token无效或已过期");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        return jwtBlacklistMapper.selectCount(
                new LambdaQueryWrapper<JwtBlacklist>()
                        .eq(JwtBlacklist::getToken, token)
                        .gt(JwtBlacklist::getExpireAt, LocalDateTime.now())
        ) > 0;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
