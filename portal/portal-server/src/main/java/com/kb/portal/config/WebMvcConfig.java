package com.kb.portal.config;

import com.marschat.auth.anonymous.AnonymousAuthProperties;
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
    private final AnonymousAuthProperties anonymousAuth;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 🔓 2026-10-15：匿名放行清单从配置读取（marschat.auth.anonymous.paths，
        //    auth-core 2.3.0 的 AnonymousAuthProperties），并与代码内「登录保底集」合并
        //    （保底集在公共库代码内合并、配置不可清空，防误配打断登录链路）；
        //    收放匿名权只改 yml + 重启，不再需要改代码发版。
        //
        //    🧭 路径口径（勿写错形态）：拦截器按「context-path 剥离后」的路径匹配
        //    （浏览器 /portal/api/** → nginx 透传 → Spring 剥 /portal → /api/**），
        //    故 yml paths 一律写**剥离后**形态，且**不配** anonymous.context-path
        //    （配了只会多生成一份永不命中的 /portal/api/** 副本，无害但误导）。
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/sys/**", "/api/auth/userinfo", "/api/auth/logout",
                        "/api/auth/change-password", "/api/auth/permissions", "/api/admin/**")
                .excludePathPatterns(anonymousAuth.effectivePaths().toArray(new String[0]));

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
