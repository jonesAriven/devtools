package com.kb.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪过滤器（Reactive GlobalFilter）。
 * <p>
 * 从请求头提取 {@code X-Trace-Id}，没有则生成 32 位无横线 UUID；
 * 注入到下游请求头并回写到响应头，供全链路日志关联。
 * <p>
 * 执行顺序最高，确保后续 JwtAuthFilter 的错误响应也能带上 traceId。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        final String finalTraceId = traceId;

        // 回写响应头（在响应提交前设置即可生效）
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        // 注入到下游请求头（先移除客户端伪造的同名头）
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(TRACE_ID_HEADER);
                    h.set(TRACE_ID_HEADER, finalTraceId);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
