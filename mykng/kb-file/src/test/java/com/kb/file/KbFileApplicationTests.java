package com.kb.file;

import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载测试。
 * <p>
 * 使用 test profile（H2 内存数据库），并在 application-test.yml 中排除 Redis / MongoDB 自动配置。
 * 外部依赖 Bean（RedisTemplate / MinioService / SearchIndexService / FileContentRepository）通过
 * @MockBean 替换，确保上下文可在无外部 MySQL/Redis/MongoDB/MinIO/MeiliSearch 环境下启动。
 */
@SpringBootTest
@ActiveProfiles("test")
class KbFileApplicationTests {

    @MockBean private RedisTemplate<String, Object> redisTemplate;
    @MockBean private MinioService minioService;
    @MockBean private SearchIndexService searchIndexService;
    @MockBean private FileContentRepository fileContentRepository;

    @Test
    void contextLoads() {
    }
}
