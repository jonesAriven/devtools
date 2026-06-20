package com.kb.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 限流配置（可选）。
 * <p>
 * KeyResolver 属于 spring-cloud-gateway 核心包，无需额外依赖即可编译。<br>
 * 实际启用 RequestRateLimiter 需引入 {@code spring-boot-starter-data-redis-reactive}，
 * 并在路由 filters 中添加：
 * <pre>
 * - name: RequestRateLimiter
 *   args:
 *     redis-rate-limiter.replenishRate: 50   # 每秒令牌生成速率
 *     redis-rate-limiter.burstCapacity: 100  # 令牌桶容量
 *     key-resolver: "#{@userKeyResolver}"
 * </pre>
 */
@Configuration
public class RateLimitConfig {

    /**
     * 已登录按用户 ID 限流，未登录按客户端 IP 限流。
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            String ip = "anonymous";
            if (exchange.getRequest().getRemoteAddress() != null) {
                ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just("ip:" + ip);
        };
    }
}
