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

    private final AnonymousAuthProperties anonymousAuth;

    public WebMvcConfig(AuthInterceptor authInterceptor, PermissionInterceptor permissionInterceptor,
                        AnonymousAuthProperties anonymousAuth) {
        this.authInterceptor = authInterceptor;
        this.permissionInterceptor = permissionInterceptor;
        this.anonymousAuth = anonymousAuth;
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
        // 🔓 2026-10-14：路径级匿名清单从配置读取（marschat.auth.anonymous.paths），
        //    并与代码内「登录保底集」合并（保底不可清空，防误配打断登录链路）。
        //    业务性放开/收回只改 yml 重启生效，详见 AnonymousAuthProperties。
        //
        //    🧭 历史脉络（排查时必读）：
        //    - 2026-09-16 P0-3 修「匿名洞」：/activation/generate 与
        //      /activation/config/default-expire 曾被整条排除出鉴权（匿名可生成/可读配置），
        //      当时从本白名单移除，改由登录 + 权限点把关。
        //    - 2026-10-12 回退：/activecode/index.html 是给外模块/客户端提供的公开自助页，
        //      依赖上述两个接口 ⇒ 改为 AuthInterceptor 的方法级白名单（路径+方法），
        //      GET default-expire 公开、PUT 仍是管理功能（authz rules: config:expire）。
        //    - 2026-10-14 配置化：两级白名单（paths / endpoints）统一收敛到
        //      marschat.auth.anonymous，代码只保留登录保底集。
        //    🔴 收回匿名权的纪律（勿只改一处，会假性失效）：generate/default-expire 的
        //       匿名权由 yml 两段共同决定 —— marschat.auth.anonymous.endpoints（放行）
        //       + marschat.authz.rules（POST generate 的 code:generate 权限点规则，当前已删）。
        //       若恢复 POST generate 的权限点拦截，只删 endpoints 不放规则 = 接口裸奔。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/activecode/main.html")
                .addPathPatterns("/activecode/api/**")
                .excludePathPatterns(anonymousAuth.effectivePaths().toArray(new String[0]));

        // P0-3 (2026-09-16): API-level permission gate (MVC HandlerInterceptor).
        // Registered AFTER authInterceptor: check 'logged in' first, then 'permission point'.
        // Only write methods matching marschat.authz.rules are gated; reads/OPTIONS pass.
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/activecode/api/**");
    }
}
