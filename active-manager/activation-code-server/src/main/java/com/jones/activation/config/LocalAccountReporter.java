package com.jones.activation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地账号上报（统一账号映射 · 应用侧，Phase 7）。
 *
 * <p>activecode（激活码系统）有自有 {@code admin_user} 账号库（会话式登录）。
 * 本上报器在启动时把该库账号**全量覆盖**登记到 auth-center 的 {@code app_account_mapping}，
 * 由中心按 username 自动认领到统一身份，使「该应用有哪些账号」在中心一处可见。
 *
 * <p><b>双模策略</b>：activecode 的本地账号统一后定位为**超管应急账号**（中心统一账号
 * 不可用时可在应用内直接登录），普通用户一律走中心统一账号。
 *
 * <p>凭据走 {@code X-Client-Secret}（{@code sys_app_client.client_secret}，由 apps-registry
 * 生成并经 auth-center 写入）；未注入时只 WARN 跳过，不阻断启动（fail-soft）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalAccountReporter implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${marschat.account.report.enabled:true}")
    private boolean enabled;

    @Value("${marschat.account.report.issuer:http://auth-center:8085}")
    private String issuer;

    @Value("${marschat.account.report.client-id:marschat-activecode}")
    private String clientId;

    @Value("${marschat.account.report.report-secret:}")
    private String reportSecret;

    public LocalAccountReporter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (reportSecret == null || reportSecret.isBlank()) {
            log.warn("[账号上报] 未配置 report-secret，跳过（activecode 本地账号未登记到中心）");
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT username FROM admin_user",
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("account", rs.getString("username"));
                        m.put("name", "激活码系统应急管理员");
                        return m;
                    });
            if (rows.isEmpty()) {
                log.info("[账号上报] activecode 无本地账号，跳过");
                return;
            }
            String body = objectMapper.writeValueAsString(Map.of("accounts", rows));
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(issuer + "/internal/clients/" + clientId + "/accounts"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Client-Secret", reportSecret)
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                log.info("[账号上报] activecode 成功：{} 个本地账号已登记（{}）", rows.size(), response.body());
            } else {
                log.warn("[账号上报] activecode 失败 HTTP {}：{}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[账号上报] activecode 异常（不阻断启动）：{}", e.getMessage());
        }
    }
}
