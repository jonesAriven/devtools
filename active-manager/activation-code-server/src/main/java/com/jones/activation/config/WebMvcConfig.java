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

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
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
                        "/activecode/api/activation/verify",
                        "/activecode/api/activation/generate",
                        "/activecode/api/activation/config/default-expire",
                        "/activecode/index.html",
                        "/activecode/downloads.html",
                        "/activecode/api/download/**",
                        "/favicon.ico"
                );
    }
}
