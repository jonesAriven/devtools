package com.kb.auth;

import com.kb.common.exception.GlobalExceptionHandler;
import com.kb.common.trace.TraceIdAutoConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * kb-auth 认证微服务启动类
 * <p>
 * 通过 @Import 引入 kb-common 的全局异常处理器和 TraceId 链路追踪。
 * @EnableAsync 启用异步支持，用于操作日志异步写入
 */
@SpringBootApplication
@MapperScan("com.kb.auth.mapper")
@Import({GlobalExceptionHandler.class, TraceIdAutoConfig.class})
@EnableAsync
@EnableDiscoveryClient
public class KbAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbAuthApplication.class, args);
    }
}
