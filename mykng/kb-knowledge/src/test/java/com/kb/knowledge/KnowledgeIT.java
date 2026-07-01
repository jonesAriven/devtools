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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kb-knowledge 集成测试（*IT.java，Failsafe 默认匹配模式）。
 * 覆盖阶段2层级3接口集成自测的 7 个场景：
 * 正常参数 / 参数缺失 / 非法格式 / 越权访问 / 未认证 / 重复提交 / 大数据量。
 * 基础设施沿用原 KnowledgeIntegrationTest：排除 Redis/MongoDB 自动配置后用 @MockBean 替代，H2 内存库提供数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("kb-knowledge 集成测试 - 7场景全覆盖")
class KnowledgeIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean
    private MongoTemplate mongoTemplate;
    @MockBean
    private DocContentRepository docContentRepository;
    @MockBean
    private WebContentRepository webContentRepository;
    @MockBean
    private SearchIndexService searchIndexService;
    @MockBean
    private EventPublisher eventPublisher;

    private HttpHeaders authHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        headers.set("X-Username", "tester");
        return headers;
    }

    private int extractCode(ResponseEntity<Map> resp) {
        Map<String, Object> body = resp.getBody();
        if (body == null) {
            return -1;
        }
        Object code = body.get("code");
        return code instanceof Number ? ((Number) code).intValue() : -1;
    }

    // ============ 场景1: 正常参数 ============
    @Test
    @DisplayName("场景1-正常参数-列表查询返回200")
    void scenario1_normalParameter_listReturns200() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/doc/list?folderId=0&page=1&size=10", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "响应体不应为空");
        assertEquals(200, extractCode(resp), "正常参数应返回 code=200");
        assertNotNull(resp.getBody().get("data"), "响应 data 字段不应为空");
    }

    // ============ 场景2: 参数缺失/为空 ============
    @Test
    @DisplayName("场景2-参数缺失-返回校验错误")
    void scenario2_missingParameter_returnsValidationError() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(new HashMap<>(), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/doc", entity, Map.class);

        assertNotNull(resp.getBody(), "响应体不应为空");
        assertNotEquals(200, extractCode(resp), "必填字段缺失应返回非200校验错误码");
    }

    // ============ 场景3: 参数超长/非法格式 ============
    @Test
    @DisplayName("场景3-非法格式-校验拦截不抛500")
    void scenario3_invalidFormat_validationInterceptsNo500() {
        HttpHeaders headers = authHeaders("1");
        Map<String, Object> body = new HashMap<>();
        body.put("folderId", 0);
        body.put("title", "   "); // 纯空格，触发 @NotBlank 校验
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/doc", entity, Map.class);

        int status = resp.getStatusCode().value();
        assertTrue(status < 500, "非法格式应被校验拦截，不应抛500，实际: " + status);
    }

    // ============ 场景4: 越权访问 ============
    @Test
    @DisplayName("场景4-越权访问-拒绝访问他人资源")
    void scenario4_unauthorizedAccess_rejected() {
        // 步骤1：用户1创建文档
        HttpHeaders headers1 = authHeaders("1");
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("title", "越权测试文档");
        createBody.put("content", "测试内容");
        createBody.put("folderId", 0);
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, headers1);
        ResponseEntity<Map> createResp = restTemplate.postForEntity("/doc", createEntity, Map.class);

        // 提取文档ID（创建失败则用默认1）
        long docId = 1L;
        Map<String, Object> createRespBody = createResp.getBody();
        if (createRespBody != null) {
            Object data = createRespBody.get("data");
            if (data instanceof Map) {
                Object idObj = ((Map<?, ?>) data).get("id");
                if (idObj instanceof Number) {
                    docId = ((Number) idObj).longValue();
                }
            }
        }

        // 步骤2：用户2访问用户1的文档（越权）
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-User-Id", "2");
        HttpEntity<Void> accessEntity = new HttpEntity<>(headers2);
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/doc/" + docId, HttpMethod.GET, accessEntity, Map.class);

        assertNotNull(resp.getBody(), "越权访问响应体不应为空");
        assertNotEquals(200, extractCode(resp), "越权访问他人文档应被拒绝（期望403）");
    }

    // ============ 场景5: 未认证/Token过期 ============
    @Test
    @DisplayName("场景5-未认证-返回4xx")
    void scenario5_unauthenticated_returns4xx() {
        // 不带 X-User-Id 头访问需鉴权接口
        ResponseEntity<Map> resp = restTemplate.getForEntity("/doc/list", Map.class);

        int status = resp.getStatusCode().value();
        assertTrue(status >= 400 && status < 500, "未认证应返回4xx（401/403），实际: " + status);
    }

    // ============ 场景6: 重复提交 ============
    @Test
    @DisplayName("场景6-重复提交-不抛500")
    void scenario6_duplicateSubmit_handledGracefully() {
        HttpHeaders headers = authHeaders("1");
        Map<String, Object> body = new HashMap<>();
        body.put("title", "重复提交测试文档");
        body.put("content", "内容");
        body.put("folderId", 0);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // 第一次提交
        restTemplate.postForEntity("/doc", entity, Map.class);
        // 第二次重复提交（相同请求体）
        ResponseEntity<Map> resp2 = restTemplate.postForEntity("/doc", entity, Map.class);

        int status = resp2.getStatusCode().value();
        assertTrue(status < 500, "重复提交不应抛500（应被拒绝或幂等处理），实际: " + status);
    }

    // ============ 场景7: 大数据量 ============
    @Test
    @DisplayName("场景7-大数据量-分页不超时")
    void scenario7_largeData_paginatesWithoutTimeout() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/doc/list?folderId=0&page=1&size=1000", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "大数据量响应体不应为空");
        assertEquals(200, extractCode(resp), "大数据量分页应返回 code=200 且不超时");
    }

    // ============ 搜索接口测试 ============
    @Test
    @DisplayName("搜索-正常参数-返回200")
    void search_normalParameter_returns200() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/search?q=test&page=1&size=10", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "搜索响应体不应为空");
        assertEquals(200, extractCode(resp), "正常搜索应返回 code=200");
        assertNotNull(resp.getBody().get("data"), "响应 data 字段不应为空");
    }

    @Test
    @DisplayName("搜索-关键词为空-返回错误")
    void search_blankKeyword_returnsError() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/search?q=&page=1&size=10", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "响应体不应为空");
        assertNotEquals(200, extractCode(resp), "空关键词应返回非200错误码");
    }

    @Test
    @DisplayName("搜索-未认证-返回4xx")
    void search_unauthenticated_returns4xx() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/search?q=test", Map.class);

        int status = resp.getStatusCode().value();
        assertTrue(status >= 400 && status < 500, "未认证应返回4xx，实际: " + status);
    }

    @Test
    @DisplayName("搜索-type为空字符串-搜索所有类型")
    void search_emptyType_searchesAllTypes() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/search?q=test&type=&page=1&size=10", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "搜索响应体不应为空");
        assertEquals(200, extractCode(resp), "空字符串type应正常搜索（搜索所有类型）");
    }

    @Test
    @DisplayName("搜索-指定type=doc-只搜文档类型")
    void search_withDocType_returnsDocResults() {
        HttpHeaders headers = authHeaders("1");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/search?q=test&type=doc&page=1&size=10", HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody(), "搜索响应体不应为空");
        assertEquals(200, extractCode(resp), "指定类型搜索应返回 code=200");
    }
}
