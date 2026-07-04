package com.kb.knowledge;

import com.kb.common.event.KbEventAutoConfig;
import com.kb.common.exception.GlobalExceptionHandler;
import com.kb.common.trace.TraceIdAutoConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * kb-knowledge 知识库微服务启动类
 * <p>
 * 通过 @Import 引入 kb-common 的全局异常处理器、TraceId 链路追踪、事件总线。
 * 端口 8083，无 context-path。
 * 包含目录/笔记/网页/搜索/分享/标签/空间/回收站/版本全部功能。
 */
@SpringBootApplication
@MapperScan("com.kb.knowledge.mapper")
@EnableFeignClients(basePackages = "com.kb.knowledge.feign")
@EnableAsync
@EnableScheduling
@Import({GlobalExceptionHandler.class, TraceIdAutoConfig.class, KbEventAutoConfig.class})
@EnableDiscoveryClient
public class KbKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbKnowledgeApplication.class, args);
    }
}
