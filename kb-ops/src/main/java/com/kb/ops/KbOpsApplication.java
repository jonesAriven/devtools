package com.kb.ops;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * kb-ops 运维微服务启动类
 * <p>
 * 通过 @Import 引入 common-core 的全局异常处理器和 TraceId 链路追踪。
 * 端口 8084，无 context-path。
 * 包含主机/服务/部署记录/运维知识/看板/矛盾检测/知识导入全部功能。
 */
@SpringBootApplication
@MapperScan("com.kb.ops.mapper")
@EnableFeignClients(basePackages = "com.kb.ops.feign")
@EnableScheduling
public class KbOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbOpsApplication.class, args);
    }
}
