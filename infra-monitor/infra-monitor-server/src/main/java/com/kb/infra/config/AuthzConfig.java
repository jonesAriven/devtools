package com.kb.infra.config;

import com.kb.infra.util.JwtUtil;
import com.marschat.auth.authz.PermissionChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * infra-monitor 接口级鉴权的装配（2026-09-15 · P1）。
 *
 * <p>机制来自公共组件 auth-core（{@code @RequirePermission} + {@code RequirePermissionInterceptor}
 * + {@code AuthzAutoConfig}；本应用此前在 application.yml 显式排除了 AuthzAutoConfig，
 * 现已放开）。本类<b>只做一件事</b>：提供一个 infra 专属的 {@link PermissionChecker} bean。
 * auth-core 的 {@code AuthzAutoConfig.permissionChecker} 标注了 {@code @ConditionalOnMissingBean}，
 * 故会被本 bean 覆盖（<b>不改 auth-core、不发新版本</b>）。
 *
 * <p>为什么 infra 必须自定义：auth-core 默认 checker 把请求头的 token 原样转发给中心，
 * 而 infra 同时存在「本应用自签 HS256」与「中心 OIDC RS256」两类 token，必须分辨处理 ——
 * 详见 {@link InfraPermissionChecker} 的类注释。
 */
@Configuration
public class AuthzConfig {

    @Bean
    public PermissionChecker permissionChecker(
            JwtUtil jwtUtil,
            @Value("${marschat.authz.issuer:http://127.0.0.1:8085}") String issuer,
            @Value("${marschat.authz.client-id:marschat-inframon}") String clientId,
            @Value("${marschat.authz.cache-ttl-ms:60000}") long cacheTtlMs,
            @Value("${marschat.authz.fail-open:false}") boolean failOpen,
            @Value("${infra.admin.username:admin}") String adminUsername,
            @Value("${infra.authz.local-admin-bypass:true}") boolean localAdminBypass) {
        return new InfraPermissionChecker(issuer, clientId, cacheTtlMs, failOpen,
                jwtUtil, adminUsername, localAdminBypass);
    }
}
