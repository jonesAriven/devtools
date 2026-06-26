package com.kb.file;

import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("kb-file 集成测试")
class FileIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    // 排除 Redis 自动配置后，RedisConfig.redisTemplate 需要 RedisConnectionFactory，
    // 这里用 MockBean 替换 RedisTemplate，跳过 RedisConfig 的 @Bean 方法。
    @MockBean private RedisTemplate<String, Object> redisTemplate;
    // 集成测试环境无 MinIO / MeiliSearch，Mock 外部服务 Bean。
    @MockBean private MinioService minioService;
    @MockBean private SearchIndexService searchIndexService;
    // 排除 MongoDB 自动配置后 FileContentRepository 无工厂创建，Mock 提供 Bean 供 FileParseServiceImpl 注入。
    @MockBean private FileContentRepository fileContentRepository;

    @Test
    @DisplayName("健康检查 - Spring上下文正常启动")
    void contextLoads() {
        // Context startup validates H2 + mocked MinIO/MeiliSearch/Redis
    }

    @Test
    @DisplayName("文件列表 - 数据库查询链路")
    void listFiles() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "admin");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/file/list?page=1&size=20", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
    }
}
