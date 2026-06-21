package com.kb.ops;

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
@DisplayName("kb-ops 集成测试")
class OpsIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("健康检查 - Spring上下文正常启动")
    void contextLoads() {
        // Context startup validates MySQL + Redis connections
    }

    @Test
    @DisplayName("主机列表 - 数据库查询链路")
    void listHosts() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "admin");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/host/list?page=1&size=20", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
    }
}
