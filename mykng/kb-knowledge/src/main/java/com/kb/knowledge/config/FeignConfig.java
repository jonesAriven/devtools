package com.kb.knowledge.config;

import com.kb.common.trace.FeignTraceIdInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class FeignConfig {

    @Bean
    public FeignTraceIdInterceptor feignTraceIdInterceptor() {
        return new FeignTraceIdInterceptor();
    }

    @Bean
    public RequestInterceptor feignUserInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                template.header("X-User-Id", auth.getName());
            }
        };
    }
}
