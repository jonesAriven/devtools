package com.kb.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * 链路追踪过滤器（Reactive GlobalFilter）。
 * <p>
 * 从请求头提取 {@code X-Trace-Id}，没有则生成 32 位无横线 UUID；
 * 注入到下游请求头并回写到响应头，同时将 traceId 放入 MDC，
 * 配合 {@link com.kb.gateway.config.WebFluxMdcConfig} 的 Reactor 自动上下文传播，
 * 使网关的所有日志输出带 traceId 字段。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC = "traceId";

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

        // 回写响应头
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        // 注入到下游请求头（先移除客户端伪造的同名头）
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(TRACE_ID_HEADER);
                    h.set(TRACE_ID_HEADER, finalTraceId);
                })
                .build();

        // 通过 Reactor Context 传播 MDC traceId
        // 配合 WebFluxMdcConfig 的 Hooks.enableAutomaticContextPropagation() 自动生效
        return chain.filter(exchange.mutate().request(mutated).build())
                .contextWrite(ctx -> {
                    MDC.put(TRACE_ID_MDC, finalTraceId);
                    return ctx;
                });
    }
}