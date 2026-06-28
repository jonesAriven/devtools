package com.kb.intelligence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 上下文加载测试。
 *
 * 通过 @ActiveProfiles("test") 激活 application-test.yml 配置：
 * - 使用 H2 内存数据库（MODE=MySQL）替代 MySQL
 * - 排除 MongoDB / Redis 自动配置，避免外部依赖
 * - InMemoryContentStorage（@Profile("!prod")）在 test 环境下生效，替代 MongoContentStorage
 *
 * DatabaseInitializer 作为 CommandLineRunner 会执行 MySQL 风格 DDL，
 * 由于 H2 对部分语法不兼容，依赖其内部 try/catch 静默处理，不影响上下文加载。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring Boot 上下文加载测试")
class KbIntelligenceApplicationTests {

    @Test
    @DisplayName("上下文加载成功 - 验证 Spring 容器能够正常启动")
    void contextLoads() {
        // 仅验证 Spring 容器能够成功加载所有 Bean，无需显式断言
    }
}
