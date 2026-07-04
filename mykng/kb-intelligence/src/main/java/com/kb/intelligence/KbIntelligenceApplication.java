package com.kb.intelligence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.kb.intelligence.mapper")
@EnableDiscoveryClient
public class KbIntelligenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(KbIntelligenceApplication.class, args);
    }
}
