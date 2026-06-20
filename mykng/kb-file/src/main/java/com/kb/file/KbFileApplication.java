package com.kb.file;

import com.kb.common.exception.GlobalExceptionHandler;
import com.kb.common.trace.TraceIdAutoConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * kb-file 文件微服务启动类
 * <p>
 * 通过 @Import 引入 kb-common 的全局异常处理器和 TraceId 链路追踪。
 * 端口 8082，无 context-path。
 */
@SpringBootApplication
@MapperScan("com.kb.file.mapper")
@EnableAsync
@Import({GlobalExceptionHandler.class, TraceIdAutoConfig.class})
public class KbFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbFileApplication.class, args);
    }
}
