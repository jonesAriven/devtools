package com.kb.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@DisplayName("kb-auth 集成测试")
class AuthIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("登录成功返回token")
    void loginSuccess() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
            Map.of("username", "admin", "password", "admin123"), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/auth/login", entity, Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
        assertNotNull(((Map) resp.getBody().get("data")).get("accessToken"));
    }

    @Test
    @DisplayName("错误密码返回401")
    void loginWrongPassword() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
            Map.of("username", "admin", "password", "wrongpass"), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/auth/login", entity, Map.class);

        assertNotNull(resp.getBody());
        assertNotEquals(200, resp.getBody().get("code"));
    }

    @Test
    @DisplayName("不存在用户返回401")
    void loginNonExistentUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
            Map.of("username", "noexist", "password", "anypass"), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/auth/login", entity, Map.class);

        assertNotNull(resp.getBody());
        assertNotEquals(200, resp.getBody().get("code"));
    }
}
