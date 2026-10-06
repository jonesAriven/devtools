package com.kb.portal.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final PortalAdminGateInterceptor portalAdminGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/sys/**", "/api/auth/userinfo", "/api/auth/logout",
                        "/api/auth/change-password", "/api/auth/permissions", "/api/admin/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/sso/**",
                        "/actuator/**"
                );

        // Phase 13：补回随 SsoController#proxyAdminCenter 一同消失的管理面角色闸门。
        // 依赖 JwtInterceptor 写入的 role attribute，故必须显式 order(1) 晚于它
        // （JwtInterceptor 与 auth-core 的 RequirePermissionInterceptor 均为默认 order 0）。
        registry.addInterceptor(portalAdminGateInterceptor)
                .addPathPatterns("/api/admin/**")
                .order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
