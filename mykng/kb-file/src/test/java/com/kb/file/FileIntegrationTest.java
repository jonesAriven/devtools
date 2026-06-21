package com.kb.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("kb-file 集成测试")
class FileIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("健康检查 - Spring上下文正常启动（含MinIO连接）")
    void contextLoads() {
        // Context startup validates MinIO + MySQL connections
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
