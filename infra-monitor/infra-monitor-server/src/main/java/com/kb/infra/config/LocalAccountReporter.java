package com.kb.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 本地账号上报（统一账号映射 · 应用侧，Phase 7）。
 *
 * <p>infra-monitor 的「本地账号」是**配置式单管理员**（{@code infra.admin.username}，
 * 无 user 表），它是双模策略里的**超管应急账号**：中心统一账号不可用时可由它在应用内
 * 直接登录。本上报器把该应急账号登记到中心的 {@code app_account_mapping}，
 * 使「该应用有哪些账号」在中心一处可见。
 *
 * <p>与既有菜单上报同构：凭据复用 {@code MARSCHAT_MENU_REPORT_SECRET}
 * （可被 {@code MARSCHAT_ACCOUNT_REPORT_SECRET} 覆盖）；issuer 走宿主内网
 * {@code http://127.0.0.1:8085}（本应用为 host 网络）；任何异常只 WARN，不阻断启动。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LocalAccountReporter implements ApplicationRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${marschat.account.report.enabled:true}")
    private boolean enabled;

    @Value("${marschat.account.report.issuer:http://127.0.0.1:8085}")
    private String issuer;

    @Value("${marschat.account.report.client-id:marschat-inframon}")
    private String clientId;

    @Value("${marschat.account.report.report-secret:}")
    private String reportSecret;

    /** 配置式应急管理员账号名 */
    @Value("${infra.admin.username:admin}")
    private String adminUsername;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (reportSecret == null || reportSecret.isBlank()) {
            log.warn("[账号上报] 未配置 report-secret，跳过（infra 应急账号未登记到中心）");
            return;
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            log.info("[账号上报] infra 无配置式管理员，跳过");
            return;
        }
        try {
            List<Map<String, Object>> rows = List.of(Map.of(
                    "account", adminUsername,
                    "name", "基础设施监控应急管理员"));
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
                log.info("[账号上报] infra 成功：应急账号 {} 已登记（{}）", adminUsername, response.body());
            } else {
                log.warn("[账号上报] infra 失败 HTTP {}：{}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[账号上报] infra 异常（不阻断启动）：{}", e.getMessage());
        }
    }
}
