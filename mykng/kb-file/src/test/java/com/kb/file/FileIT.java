package com.kb.file;

import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.FileParseTrigger;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("kb-file 集成测试 - 7场景全覆盖")
class FileIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    // 排除 Redis 自动配置后，RedisConfig.redisTemplate 需要 RedisConnectionFactory，
    // 这里用 MockBean 替换 RedisTemplate，跳过 RedisConfig 的 @Bean 方法。
    @MockBean private RedisTemplate<String, Object> redisTemplate;
    // 集成测试环境无 MinIO / MeiliSearch，Mock 外部服务 Bean。
    @MockBean private MinioService minioService;
    @MockBean private SearchIndexService searchIndexService;
    // 排除 MongoDB 自动配置后 FileContentRepository 无工厂创建，Mock 提供 Bean 供 FileParseServiceImpl 注入。
    @MockBean private FileContentRepository fileContentRepository;
    // 重复提交场景中，Mock 异步解析触发器避免触发真实解析链路。
    @MockBean private FileParseTrigger fileParseTrigger;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long TEST_FILE_ID = 8888L;

    private HttpHeaders userHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-Username", "tester");
        return headers;
    }

    /** 插入测试文件（user_id=1），用 MERGE 保证可重复执行 */
    private void insertTestFile() {
        jdbcTemplate.update(
            "MERGE INTO file (id, user_id, name, type, size, parse_status, starred, deleted) " +
            "KEY(id) VALUES (?, ?, 'test.txt', 'txt', 100, 'COMPLETED', 0, 0)",
            TEST_FILE_ID, USER_ID);
    }

    // ============ 场景1: 正常参数 ============
    @Test
    @DisplayName("场景1-正常参数-文件列表返回200")
    void scenario1_normalParameter_listFiles() {
        HttpHeaders headers = userHeaders(USER_ID);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/file/list?page=1&size=20", HttpMethod.GET, entity, Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
        assertNotNull(resp.getBody().get("data"));
    }

    // ============ 场景2: 参数缺失/为空 ============
    @Test
    @DisplayName("场景2-参数缺失-合并请求必填字段为空返回校验错误")
    void scenario2_missingParameter_returnValidationError() {
        HttpHeaders headers = userHeaders(USER_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 空请求体，缺少 fileId/name/folderId 必填字段
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(new HashMap<>(), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/file/merge", entity, Map.class);

        assertNotNull(resp.getBody());
        assertNotEquals(200, resp.getBody().get("code"));
        assertTrue(resp.getStatusCode().is4xxClientError());
    }

    // ============ 场景3: 参数超长/非法格式 ============
    @Test
    @DisplayName("场景3-非法格式-纯空白文件名被校验拦截不抛500")
    void scenario3_invalidFormat_validationRejectsNo500() {
        HttpHeaders headers = userHeaders(USER_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // name 为纯空白字符，@NotBlank 校验拦截
        Map<String, Object> body = new HashMap<>();
        body.put("fileId", "f1");
        body.put("name", "   ");
        body.put("folderId", 1);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/file/merge", entity, Map.class);

        // 校验拦截，不抛 500
        assertTrue(resp.getStatusCode().value() < 500);
        assertNotNull(resp.getBody());
        assertNotEquals(200, resp.getBody().get("code"));
    }

    // ============ 场景4: 越权访问 ============
    @Test
    @DisplayName("场景4-越权访问-其他用户访问文件返回非200")
    void scenario4_unauthorizedAccess_otherUserRejected() {
        insertTestFile(); // 文件属于 user_id=1

        // 用 X-User-Id=2 访问 user_id=1 的文件
        HttpHeaders headers = userHeaders(OTHER_USER_ID);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/file/" + TEST_FILE_ID, HttpMethod.GET, entity, Map.class);

        assertNotNull(resp.getBody());
        // 越权访问：getById 校验 userId 不匹配，返回 404 业务码
        assertNotEquals(200, resp.getBody().get("code"));
    }

    // ============ 场景5: 未认证/Token过期 ============
    @Test
    @DisplayName("场景5-未认证-不带X-User-Id返回4xx")
    void scenario5_unauthenticated_return4xx() {
        // 不带 X-User-Id 头访问需鉴权的 /file/list
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/file/list?page=1&size=20", HttpMethod.GET, entity, Map.class);

        // 未认证应返回 4xx（401/403）
        assertTrue(resp.getStatusCode().is4xxClientError());
    }

    // ============ 场景6: 重复提交 ============
    @Test
    @DisplayName("场景6-重复触发解析-幂等处理返回200")
    void scenario6_duplicateSubmit_idempotentReparse() {
        insertTestFile(); // 文件属于 user_id=1
        HttpHeaders headers = userHeaders(USER_ID);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // 连续 POST 两次重新解析
        ResponseEntity<Map> first = restTemplate.exchange(
            "/file/" + TEST_FILE_ID + "/reparse", HttpMethod.POST, entity, Map.class);
        ResponseEntity<Map> second = restTemplate.exchange(
            "/file/" + TEST_FILE_ID + "/reparse", HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        // 第二次提交幂等处理（FileParseTrigger 被 Mock，重复触发不产生副作用）
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals(200, second.getBody().get("code"));
    }

    // ============ 场景7: 大数据量 ============
    @Test
    @DisplayName("场景7-大数据量-分页size=1000不超时")
    void scenario7_largeData_paginationNotTimeout() {
        HttpHeaders headers = userHeaders(USER_ID);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
            "/file/list?page=1&size=1000", HttpMethod.GET, entity, Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().get("code"));
    }
}
