package com.jones.activation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 菜单/权限点注册表上报（Phase 11，2026-09-15）。
 *
 * <p>activecode 是 6 个自研应用里**唯一完全没有上报权限点**的：既没有 menu-registry.yml，
 * 也没有任何上报代码 —— 中心 {@code sys_permission} 里没有 activecode 的菜单/api 点，
 * 导致跨应用授权、菜单权限、接口闸门对它全部失效。本类在启动时把 classpath 下的
 * {@code menu-registry.yml} **全量覆盖**上报到 auth-center
 * {@code PUT /internal/clients/{clientId}/menus}。
 *
 * <p><b>为什么不用 com.marschat:auth-core 的 MenuRegistryReporter</b>：activecode 未引入
 * common-core / auth-core，而 auth-core 自带 JWT/Authz 等一整套自动装配，在这里引入有撞 bean
 * 的历史风险（见 ADR §18.10 决策 2）。故按本应用已有的 {@link LocalAccountReporter} 同口径
 * 做等价实现：{@code X-Client-Secret} + internal 端点，零新增依赖。
 *
 * <p>凭据 = {@code sys_app_client.client_secret}（apps-registry 生成、auth-center 幂等写库），
 * 经 compose 注入 {@code MARSCHAT_MENU_REPORT_SECRET}；未注入时只 WARN 跳过，不阻断启动。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MenuRegistryReporter implements ApplicationRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${marschat.menu.report.enabled:true}")
    private boolean enabled;

    @Value("${marschat.menu.report.client-id:marschat-activecode}")
    private String clientId;

    @Value("${marschat.menu.report.report-secret:}")
    private String reportSecret;

    /** 复用「统一认证中心服务端互调基址」：activecode 在独立网络，必须走宿主 LAN 地址，不能用容器名。 */
    @Value("${marschat.auth-center.base:http://192.168.31.105:8085}")
    private String issuer;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (reportSecret == null || reportSecret.isBlank()) {
            log.warn("[菜单上报] 未配置 report-secret，跳过（activecode 权限点未登记到中心）");
            return;
        }
        try {
            ClassPathResource res = new ClassPathResource("menu-registry.yml");
            if (!res.exists()) {
                log.warn("[菜单上报] classpath 下无 menu-registry.yml，跳过");
                return;
            }
            String menusYaml = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String body = objectMapper.writeValueAsString(Map.of("menusYaml", menusYaml));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(issuer.replaceAll("/+$", "") + "/internal/clients/" + clientId + "/menus"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Client-Secret", reportSecret)
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                log.info("[菜单上报] activecode 成功（client={}）：{}", clientId, response.body());
            } else {
                log.warn("[菜单上报] activecode 失败 HTTP {}：{}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[菜单上报] activecode 异常（不阻断启动）：{}", e.getMessage());
        }
    }
}
