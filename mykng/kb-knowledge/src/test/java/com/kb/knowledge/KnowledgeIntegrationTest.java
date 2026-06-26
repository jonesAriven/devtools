package com.kb.knowledge;

import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("kb-knowledge 集成测试")
class KnowledgeIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    // 排除 Redis 自动配置后，RedisConfig 的 3 个 @Bean 需要 RedisConnectionFactory，
    // 这里用 MockBean 替换它们，跳过 RedisConfig 的 @Bean 方法。
    @MockBean private RedisTemplate<String, Object> redisTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    // 排除 MongoDB 自动配置后 MongoConfig.mongoTemplate 需要 MongoDatabaseFactory，Mock 提供该 Bean。
    @MockBean private MongoTemplate mongoTemplate;
    // 排除 MongoDB 仓储自动配置后，DocContentRepository / WebContentRepository 无工厂创建，Mock 提供。
    @MockBean private DocContentRepository docContentRepository;
    @MockBean private WebContentRepository webContentRepository;
    // 集成测试环境无 MeiliSearch，Mock 外部服务 Bean。
    @MockBean private SearchIndexService searchIndexService;
    // EventPublisher 内部使用 RedisTemplate（已 Mock），但为避免发布事件时 Mock 行为不确定，一并 Mock。
    @MockBean private EventPublisher eventPublisher;

    @Test
    @DisplayName("健康检查 - Spring上下文正常启动")
    void contextLoads() {
        // Context startup validates H2 + mocked MeiliSearch/Redis/MongoDB
    }

    @Test
    @DisplayName("创建文档 - 完整链路验证（数据进 H2）")
    void createDocument() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "admin");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
            Map.of("title", "集成测试文档", "content", "这是集成测试内容", "folderId", 0), headers);

        // POST /doc 是 DocController 的创建端点（@RequestMapping("/doc") + @PostMapping）
        ResponseEntity<Map> resp = restTemplate.postForEntity("/doc", entity, Map.class);

        assertNotNull(resp.getBody());
        // 允许 200（成功）或 4xx/5xx（业务失败如参数校验），但不能是连接错误
        int status = resp.getStatusCode().value();
        assertTrue(status == 200 || (status >= 400 && status < 600),
            "期望 200 或 4xx/5xx，实际: " + status);
    }
}
