package com.kb.ops;

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
@DisplayName("kb-ops 集成测试")
class OpsIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @MockBean private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("健康检查 - Spring上下文正常启动")
    void contextLoads() {
        // H2 内存数据库 + 排除 Redis 自动配置，验证上下文可启动
    }

    @Test
    @DisplayName("主机列表 - 数据库查询链路")
    void listHosts() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1");
        headers.set("X-Username", "admin");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        // HostController @RequestMapping("/ops/host")，完整路径为 /ops/host/list
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/ops/host/list?page=1&size=20", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
    }
}
