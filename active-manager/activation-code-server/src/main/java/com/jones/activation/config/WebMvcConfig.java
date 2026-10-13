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
        // ⚠️ 2026-10-13 订正（排查「HTML 无缓存头」时的关键背景，勿再怀疑本配置未注册）：
        //    本配置**只服务静态资产**（css/js/img，如 sso.js、marschat-auth-core.umd.js）——
        //    它们不在 PageController 的映射里，由本 resource handler 提供，
        //    实测响应带齐 Last-Modified + Cache-Control: no-cache, must-revalidate + Accept-Ranges。
        // 🔴 HTML 页面（main/index/login/sso-callback/downloads/members.html）全部由
        //    PageController 以 @RestController 直出，抢在本 handler 之前命中
        //    （RequestMappingHandlerMapping order=0 < SimpleUrlHandlerMapping 的
        //    LOWEST_PRECEDENCE-1），本处的 setCacheControl 对 HTML 路径不生效。
        //    HTML 的缓存头必须在 PageController#readStaticFile 单独设置（no-store，2026-10-13）。
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
                        // 生成激活码、读默认有效期配置。二者当时被移出本白名单。
                        //
                        // 🔄 2026-10-12 回退（本次）：/activecode/index.html 是给外模块 /
                        //    客户端提供的公开自助页（页面本身一直在本白名单内），依赖的正是
                        //    这两个接口。收口后「页面打得开，但生成按钮必 401、有效期永远卡
                        //    在『加载中...』」—— 页面放行与接口收口自相矛盾。
                        //    现改为 {@link AuthInterceptor#ANONYMOUS_ENDPOINTS} 方法级白名单，
                        //    按「路径 + 方法」放行：POST /generate、GET /config/default-expire。
                        //    🔴 若日后要收回匿名权：必须**同时**改 AuthInterceptor 白名单与
                        //       application.yml 的 authz 规则表，只改一处会假性失效。
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
