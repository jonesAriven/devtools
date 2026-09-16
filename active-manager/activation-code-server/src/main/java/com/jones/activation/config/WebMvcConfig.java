package com.jones.activation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, PermissionInterceptor permissionInterceptor) {
        this.authInterceptor = authInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/activecode/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/activecode/main.html")
                .addPathPatterns("/activecode/api/**")
                .excludePathPatterns(
                        "/activecode/login.html",
                        "/activecode/sso-callback.html",
                        "/activecode/api/auth/login",
                        "/activecode/api/auth/sso-login",
                        "/activecode/api/auth/session",
                        // 统一登录三方式补齐（L1，2026-09-15）：邮箱码登录与自助改密都发生在
                        // 「还没有本地会话」时，必须匿名可达，否则用户根本进不了登录流程。
                        "/activecode/api/auth/mail-login",
                        "/activecode/api/auth/mail-login/send-code",
                        "/activecode/api/auth/forgot-password",
                        "/activecode/api/auth/reset-password",
                        // P0-3（2026-09-16）修「匿名洞」：/activation/generate 与
                        // /activation/config/default-expire 曾整条排除出鉴权 —— 匿名即可
                        // 生成激活码、读默认有效期配置。二者已从白名单移除，改由
                        // AuthInterceptor（登录）+ PermissionInterceptor（权限点）共同把关。
                        // ⚠️ /activation/verify 保留匿名（**待产品确认是否对外**），语义未动。
                        "/activecode/api/activation/verify",
                        "/activecode/index.html",
                        "/activecode/downloads.html",
                        "/activecode/api/download/**",
                        "/favicon.ico"
                );

        // P0-3 (2026-09-16): API-level permission gate (MVC HandlerInterceptor).
        // Registered AFTER authInterceptor: check 'logged in' first, then 'permission point'.
        // Only write methods matching marschat.authz.rules are gated; reads/OPTIONS pass.
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/activecode/api/**");
    }
}
