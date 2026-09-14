package com.kb.infra.config;

import com.marschat.auth.oidc.OidcTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一鉴权 · OIDC 验签器装配（Phase 7 收敛）。
 *
 * <p>背景：infra-monitor-server 在 {@code application.yml} 里显式 **exclude 掉 auth-core 的
 * {@code AuthJwtAutoConfig}**（该装配会连带创建 {@code TokenProvider}，要求
 * {@code marschat.auth.secret} ≥32B，本应用有自己的 JWT 体系，不需要）。因此 auth-core 的
 * {@code OidcTokenVerifier} 不会自动成为 bean。
 *
 * <p>解法：在此手动声明该 bean。@Bean 方法返回的实例同样会经过 Spring 的
 * {@code AutowiredAnnotationBeanPostProcessor}，因此类内 {@code @Value} 字段（
 * {@code marschat.oidc.issuer} / {@code marschat.oidc.jwks-uri}）会被正常注入 —— 只需在
 * {@code application.yml} 提供这两个属性。
 *
 * <p>收益：**RS256 验签实现全平台唯一**（auth-core），本应用不再维护第二份副本；
 * 与 kb-gateway / kb-ops 的验签口径（JWKS TTL 10min、按 kid 匹配、缺失回退单 key、
 * 校验 issuer 与过期）天然一致。
 */
@Configuration
public class OidcConfig {

    @Bean
    public OidcTokenVerifier oidcTokenVerifier() {
        return new OidcTokenVerifier();
    }
}
