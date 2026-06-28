package com.kb.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TraceIdFilter 单元测试。
 * <p>
 * 该过滤器为 Reactive GlobalFilter，因此使用 {@link MockServerHttpRequest} /
 * {@link MockServerWebExchange}（即 Reactive 栈的 request/response 模拟对象，
 * 等价于 Servlet 栈的 HttpServletRequest/Response）构造测试上下文，不依赖外部环境。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TraceId 链路追踪过滤器单元测试")
class TraceIdFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private final TraceIdFilter filter = new TraceIdFilter();

    /** 请求无 traceId → 自动生成 32 位无横线 UUID 并写入请求/响应头 */
    @Test
    @DisplayName("请求无 traceId → 自动生成 32 位 UUID 并写入请求/响应头")
    void noTraceId_generatesNewOne() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        // 响应头已回写 traceId
        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(responseTraceId, "响应头应包含 traceId");
        assertEquals(32, responseTraceId.length(), "traceId 应为 32 位无横线 UUID");
        assertFalse(responseTraceId.contains("-"), "traceId 不应包含横线");

        // 下游请求头也已注入 traceId
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        String downstreamTraceId = captor.getValue()
                .getRequest()
                .getHeaders()
                .getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertEquals(responseTraceId, downstreamTraceId, "下游请求头 traceId 应与响应头一致");
    }

    /** 请求已有 traceId → 沿用原值，不重新生成 */
    @Test
    @DisplayName("请求已有 traceId → 沿用原值")
    void hasTraceId_reusesExisting() {
        String existing = "abc123def456789";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/kb/api/doc/list")
                .header(TraceIdFilter.TRACE_ID_HEADER, existing)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();

        // 响应头沿用请求中的 traceId
        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertEquals(existing, responseTraceId, "响应头应沿用请求中的 traceId");

        // 下游请求头也保持一致
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        String downstreamTraceId = captor.getValue()
                .getRequest()
                .getHeaders()
                .getFirst(TraceIdFilter.TRACE_ID_HEADER);
        assertEquals(existing, downstreamTraceId, "下游请求头应沿用请求中的 traceId");
        assertTrue(downstreamTraceId.equals(existing), "traceId 应与请求中一致");
    }
}
