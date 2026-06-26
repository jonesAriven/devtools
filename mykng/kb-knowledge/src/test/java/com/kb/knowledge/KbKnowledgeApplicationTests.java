package com.kb.knowledge;

import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载测试。
 * <p>
 * 使用 test profile（H2 内存数据库），并在 application-test.yml 中排除 Redis / MongoDB 自动配置。
 * 外部依赖 Bean（RedisTemplate / StringRedisTemplate / RedisMessageListenerContainer / MongoTemplate /
 * DocContentRepository / WebContentRepository / SearchIndexService / EventPublisher）通过 @MockBean 替换，
 * 确保上下文可在无外部 MySQL/Redis/MongoDB/MeiliSearch 环境下启动。
 */
@SpringBootTest
@ActiveProfiles("test")
class KbKnowledgeApplicationTests {

    @MockBean private RedisTemplate<String, Object> redisTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private MongoTemplate mongoTemplate;
    @MockBean private DocContentRepository docContentRepository;
    @MockBean private WebContentRepository webContentRepository;
    @MockBean private SearchIndexService searchIndexService;
    @MockBean private EventPublisher eventPublisher;

    @Test
    void contextLoads() {
    }
}
