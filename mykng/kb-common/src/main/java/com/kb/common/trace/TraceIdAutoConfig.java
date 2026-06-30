package com.kb.common.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * TraceId 自动配置（P0 新增）
 * <p>
 * 仅在 Servlet MVC 应用中生效（WebFlux 应用如 kb-gateway 不加载，避免 WebMvcConfigurer 类找不到）。
 * 各 Spring MVC 服务只需引入 kb-common 依赖即可自动注册 TraceId 拦截器。
 * <p>
 * 同时注册 WebLogAspect 切面，自动记录 Controller 方法的入参、出参、耗时，并带 traceId。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdAutoConfig implements WebMvcConfigurer {

    @Bean
    public TraceIdInterceptor traceIdInterceptor() {
        return new TraceIdInterceptor();
    }

    @Bean
    public WebLogAspect webLogAspect() {
        return new WebLogAspect();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor())
                .addPathPatterns("/**");
    }
}
