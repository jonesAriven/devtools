package com.kb.portal.config;

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
 * <p>背景：平台初衷是「用户统一管理 + 账号映射」。portal 有自有 {@code sys_user} 账号库
 * （legacy 本地登录 + SSO JIT 落地），中心此前看不到这些账号。本上报器在启动时把 portal
 * 的本地账号清单**全量覆盖**登记到 auth-center 的 {@code app_account_mapping}，由中心
 * 按 username/email 自动认领到统一身份，未认领的交管理员在中心界面手工绑定。
 *
 * <p>与既有菜单上报（auth-core {@code MenuRegistryReporter}）同构：
 * <ul>
 *   <li>凭据复用 {@code MARSCHAT_MENU_REPORT_SECRET}（可被 {@code MARSCHAT_ACCOUNT_REPORT_SECRET} 覆盖），
 *       不新增服务器端密钥；</li>
 *   <li>走容器内网 {@code http://auth-center:8085}（{@code /internal/**} 不在公网白名单）；</li>
 *   <li>fail-soft：任何异常只 WARN，绝不阻断应用启动。</li>
 * </ul>
 *
 * <p><b>双模策略</b>：portal 的本地账号在统一化后仅作为**超管应急账号**保留可用，
 * 普通用户一律走中心统一账号；本上报器把两者都登记到中心，使「哪些账号有哪些系统的账号」
 * 可在一处看清。
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

    @Value("${marschat.account.report.client-id:marschat-portal}")
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
            log.warn("[账号上报] 未配置 report-secret，跳过（portal 本地账号未登记到中心）");
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT username, nickname FROM sys_user WHERE deleted = 0 AND status = 1",
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("account", rs.getString("username"));
                        m.put("name", rs.getString("nickname"));
                        return m;
                    });
            if (rows.isEmpty()) {
                log.info("[账号上报] portal 无活跃本地账号，跳过");
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
                log.info("[账号上报] portal 成功：{} 个本地账号已登记（{}）", rows.size(), response.body());
            } else {
                log.warn("[账号上报] portal 失败 HTTP {}：{}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[账号上报] portal 异常（不阻断启动）：{}", e.getMessage());
        }
    }
}
