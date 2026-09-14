package com.kb.gateway.config;

import com.marschat.auth.oidc.OidcTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一鉴权 · OIDC 验签器装配（Phase 7 收敛）。
 *
 * <p>背景：本网关是 **WebFlux** 应用，而 auth-core 的 {@code AuthJwtAutoConfig} 带
 * {@code @ConditionalOnWebApplication(SERVLET)}，在网关里不会激活，因此 auth-core 的
 * {@code OidcTokenVerifier} 不会自动成为 bean。
 *
 * <p>解法：手动声明。@Bean 返回的实例同样会经过 Spring 的注解注入后处理器，类内
 * {@code @Value("${marschat.oidc.issuer}")} / {@code @Value("${marschat.oidc.jwks-uri}")}
 * 会被正常注入 —— 本应用 {@code application.yml} 早已提供这两个键（jwks 走容器内网
 * {@code http://auth-center:8085/oauth2/jwks}）。
 *
 * <p>收益：**RS256 验签实现全平台唯一**（kb-gateway 原先自带一份
 * {@code com.kb.gateway.filter.OidcTokenVerifier} 副本，已删除），与 kb-ops / infra-monitor
 * 口径完全一致，杜绝"同一逻辑多处各抄一份"。
 */
@Configuration
public class OidcConfig {

    @Bean
    public OidcTokenVerifier oidcTokenVerifier() {
        return new OidcTokenVerifier();
    }
}
