package com.kb.portal.config;

import com.kb.portal.service.AuthCenterService;
import com.kb.portal.util.JwtUtil;
import com.marschat.auth.authz.PermissionChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * portal 接口级鉴权的装配（Phase 9 / G3 · 参考实现）。
 *
 * <p>机制来自公共组件 auth-core（{@code @RequirePermission} + {@code RequirePermissionInterceptor}
 * + {@code AuthzAutoConfig}），本类**只做一件事**：提供一个 portal 专属的 {@link PermissionChecker} bean。
 * auth-core 的 {@code AuthzAutoConfig.permissionChecker} 标注了 {@code @ConditionalOnMissingBean}，
 * 故会被本 bean 覆盖（**不改 auth-core、不发新版本**）。其余服务仍用 auth-core 默认实现（RS256 应用）。
 *
 * <p>为什么 portal 必须自定义：auth-core 默认 checker 会用请求头的 HS256 portal_token 去调中心，
 * 必然失败 → 默认 fail-open 会静默放行；详见 {@link PortalPermissionChecker} 的类注释。
 */
@Configuration
public class AuthzConfig {

    @Bean
    public PermissionChecker permissionChecker(
            AuthCenterService authCenterService,
            JwtUtil jwtUtil,
            @Value("${marschat.authz.issuer:${MARSCHAT_OIDC_ISSUER:https://auth.marschat.online}}") String issuer,
            @Value("${marschat.authz.client-id:marschat-portal}") String clientId,
            @Value("${marschat.authz.cache-ttl-ms:60000}") long cacheTtlMs) {
        return new PortalPermissionChecker(authCenterService, jwtUtil, issuer, clientId, cacheTtlMs);
    }
}
