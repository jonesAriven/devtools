package com.kb.knowledge.config;

import com.kb.common.trace.FeignTraceIdInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 配置
 * <p>
 * 注册 FeignTraceIdInterceptor，使 Feign 调用时自动传递 X-Trace-Id 头，
 * 实现跨服务链路追踪。
 */
@Configuration
public class FeignConfig {

    @Bean
    public FeignTraceIdInterceptor feignTraceIdInterceptor() {
        return new FeignTraceIdInterceptor();
    }
}
