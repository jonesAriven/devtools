package com.kb.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import java.util.Map;

/**
 * WebFlux MDC 上下文传播配置。
 * <p>
 * WebFlux 是 Reactive 模型，不使用 ThreadLocal，因此 MDC 不会自动传播。
 * 通过 Reactor 的 {@link Hooks#enableAutomaticContextPropagation()} 告诉 Reactor
 * 在 Operator 链之间自动传递 MDC 上下文。
 * <p>
 * 配合 {@link com.kb.gateway.filter.TraceIdFilter} 将 traceId 写入 MDC，
 * 即可在网关的所有日志中带上 traceId 字段。
 * <p>
 * 注意：此方法要求 Reactor 3.5.8+，Spring Boot 3.2.x 自带 Reactor 3.6.x，满足条件。
 */
@Configuration
public class WebFluxMdcConfig {

    @PostConstruct
    public void enableMdcPropagation() {
        // 启用 Reactor 自动上下文传播（3.5.8+ 可用）
        // 这会让 MDC 在 reactive operator 链中自动传递
        Hooks.enableAutomaticContextPropagation();
    }
}