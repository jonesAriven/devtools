package com.kb.common.trace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * TraceId 自动配置（P0 新增）
 * <p>
 * 各服务只需引入 kb-common 依赖即可自动注册 TraceId 拦截器。
 */
@Configuration
public class TraceIdAutoConfig implements WebMvcConfigurer {

    @Bean
    public TraceIdInterceptor traceIdInterceptor() {
        return new TraceIdInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor())
                .addPathPatterns("/api/**");
    }
}
