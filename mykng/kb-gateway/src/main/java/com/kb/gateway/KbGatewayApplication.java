package com.kb.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * kb-gateway 启动类。
 * <p>
 * Reactive 网关（基于 Spring Cloud Gateway / WebFlux）。<br>
 * 注意：不扫描 com.kb.common 下的 servlet 相关配置（TraceIdAutoConfig、GlobalExceptionHandler），
 * 因此不会与 WebFlux 冲突；仅复用 {@code com.kb.common.result.Result} 用于统一错误响应。
 */
@SpringBootApplication
public class KbGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbGatewayApplication.class, args);
    }
}
