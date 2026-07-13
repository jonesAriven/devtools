package com.kb.infra.service;

import com.kb.infra.entity.InfraHealthLog;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraHealthLogRepository;
import com.kb.infra.repository.InfraItemRepository;
import com.kb.infra.util.CryptoUtil;
import com.kb.infra.util.SshUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class HealthCheckService {

    private final InfraItemRepository itemRepository;
    private final InfraHealthLogRepository healthLogRepository;
    private final RestClient restClient;
    private final SshUtil sshUtil;
    private final CryptoUtil cryptoUtil;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public HealthCheckService(InfraItemRepository itemRepository,
                              InfraHealthLogRepository healthLogRepository,
                              SshUtil sshUtil,
                              CryptoUtil cryptoUtil) {
        this.itemRepository = itemRepository;
        this.healthLogRepository = healthLogRepository;
        this.restClient = RestClient.builder().build();
        this.sshUtil = sshUtil;
        this.cryptoUtil = cryptoUtil;
    }

    public Map<String, Object> checkAll() {
        List<InfraItem> services = itemRepository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc("service", 0)
                .stream()
                .filter(s -> {
                    Object enabled = s.getExtra() != null ? s.getExtra().get("enabled") : null;
                    if (enabled == null) return true;
                    if (enabled instanceof Boolean) return (Boolean) enabled;
                    if (enabled instanceof Number) return ((Number) enabled).intValue() == 1;
                    return false;
                })
                .toList();

        List<CompletableFuture<Map<String, Object>>> futures = services.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> checkOne(s), executor))
                .toList();

        List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long online = results.stream().filter(r -> "ONLINE".equals(r.get("status"))).count();
        long offline = results.stream().filter(r -> "OFFLINE".equals(r.get("status"))).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", results.size());
        summary.put("online", online);
        summary.put("offline", offline);
        summary.put("unknown", results.size() - online - offline);
        summary.put("results", results);
        return summary;
    }

    public Map<String, Object> checkOne(String serviceId) {
        InfraItem service = itemRepository.findByIdAndDeleted(serviceId, 0)
                .orElseThrow(() -> new RuntimeException("服务不存在: " + serviceId));
        return checkOne(service);
    }

    private Map<String, Object> checkOne(InfraItem service) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", service.getId());
        result.put("name", service.getName());

        String checkType = service.getExtra() != null ?
                (String) service.getExtra().get("checkType") : "HTTP";
        checkType = checkType != null ? checkType.toUpperCase() : "HTTP";
        result.put("checkType", checkType);

        return switch (checkType) {
            case "SHELL", "SSH", "SCRIPT" -> checkShell(service, result);
            default -> checkHttp(service, result);
        };
    }

    private Map<String, Object> checkHttp(InfraItem service, Map<String, Object> result) {
        String checkUrl = service.getExtra() != null ?
                (String) service.getExtra().get("healthCheckUrl") : null;
        if (checkUrl == null || checkUrl.isEmpty()) {
            checkUrl = service.getExtra() != null ? (String) service.getExtra().get("url") : null;
        }
        result.put("checkUrl", checkUrl);

        if (checkUrl == null || checkUrl.isEmpty()) {
            result.put("status", "UNKNOWN");
            result.put("latencyMs", null);
            result.put("errorMsg", "未配置检查地址");
            updateServiceStatus(service, "UNKNOWN", null, "未配置检查地址");
            saveLog(service, checkUrl, "UNKNOWN", null, "未配置检查地址");
            return result;
        }

        long start = System.currentTimeMillis();
        try {
            restClient.head().uri(checkUrl).retrieve().toBodilessEntity();
            long latency = System.currentTimeMillis() - start;
            result.put("status", "ONLINE");
            result.put("latencyMs", (int) latency);
            result.put("errorMsg", null);
            updateServiceStatus(service, "ONLINE", (int) latency, null);
            saveLog(service, checkUrl, "ONLINE", (int) latency, null);
        } catch (Exception e) {
            try {
                restClient.get().uri(checkUrl).retrieve().toBodilessEntity();
                long latency = System.currentTimeMillis() - start;
                result.put("status", "ONLINE");
                result.put("latencyMs", (int) latency);
                result.put("errorMsg", null);
                updateServiceStatus(service, "ONLINE", (int) latency, null);
                saveLog(service, checkUrl, "ONLINE", (int) latency, null);
            } catch (Exception ex) {
                result.put("status", "OFFLINE");
                result.put("latencyMs", null);
                result.put("errorMsg", ex.getMessage());
                updateServiceStatus(service, "OFFLINE", null, ex.getMessage());
                saveLog(service, checkUrl, "OFFLINE", null, ex.getMessage());
            }
        }
        return result;
    }

    private Map<String, Object> checkShell(InfraItem service, Map<String, Object> result) {
        Map<String, Object> extra = service.getExtra() != null ? service.getExtra() : new HashMap<>();
        String script = (String) extra.get("script");
        String credentialId = (String) extra.get("credentialId");
        String hostId = (String) extra.get("hostId");
        Object timeoutObj = extra.get("timeout");
        int timeout = timeoutObj != null ? ((Number) timeoutObj).intValue() : 30;

        result.put("script", script);

        if (script == null || script.isEmpty()) {
            result.put("status", "UNKNOWN");
            result.put("latencyMs", null);
            result.put("errorMsg", "未配置检测脚本");
            updateServiceStatus(service, "UNKNOWN", null, "未配置检测脚本");
            saveLog(service, "shell://script", "UNKNOWN", null, "未配置检测脚本");
            return result;
        }

        String host = null;
        int port = 22;
        String username = null;
        String password = null;

        if (hostId != null && !hostId.isEmpty()) {
            try {
                InfraItem hostItem = itemRepository.findByIdAndDeleted(hostId, 0).orElse(null);
                if (hostItem != null && hostItem.getExtra() != null) {
                    host = (String) hostItem.getExtra().get("ip");
                    if (host == null || host.isEmpty()) host = hostItem.getName();
                    Object portObj = hostItem.getExtra().get("sshPort");
                    if (portObj != null) port = ((Number) portObj).intValue();
                }
            } catch (Exception ignored) {}
        }

        if (credentialId != null && !credentialId.isEmpty()) {
            try {
                InfraItem credItem = itemRepository.findByIdAndDeleted(credentialId, 0).orElse(null);
                if (credItem != null && credItem.getExtra() != null) {
                    username = (String) credItem.getExtra().get("username");
                    String encryptedPwd = (String) credItem.getExtra().get("passwordEncrypted");
                    if (encryptedPwd != null) {
                        password = cryptoUtil.decrypt(encryptedPwd);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (host == null || username == null || password == null) {
            result.put("status", "UNKNOWN");
            result.put("latencyMs", null);
            result.put("errorMsg", "主机或SSH凭据配置不完整");
            updateServiceStatus(service, "UNKNOWN", null, "主机或SSH凭据配置不完整");
            saveLog(service, "shell://script", "UNKNOWN", null, "主机或SSH凭据配置不完整");
            return result;
        }

        long start = System.currentTimeMillis();
        SshUtil.SshResult sshResult = sshUtil.execute(host, port, username, password, script, timeout);
        long latency = System.currentTimeMillis() - start;

        if (sshResult.isSuccess()) {
            result.put("status", "ONLINE");
            result.put("latencyMs", (int) latency);
            result.put("errorMsg", null);
            result.put("stdout", sshResult.getStdout());
            updateServiceStatus(service, "ONLINE", (int) latency, null);
            saveLog(service, "shell://script", "ONLINE", (int) latency,
                    sshResult.getStdout() != null && sshResult.getStdout().length() > 200 ?
                            sshResult.getStdout().substring(0, 200) : sshResult.getStdout());
        } else {
            result.put("status", "OFFLINE");
            result.put("latencyMs", (int) latency);
            String err = sshResult.getStderr() != null ? sshResult.getStderr() :
                    (sshResult.getStdout() != null ? sshResult.getStdout() : "执行失败");
            result.put("errorMsg", err);
            result.put("stdout", sshResult.getStdout());
            result.put("stderr", sshResult.getStderr());
            result.put("exitCode", sshResult.getExitCode());
            updateServiceStatus(service, "OFFLINE", (int) latency,
                    err.length() > 200 ? err.substring(0, 200) : err);
            saveLog(service, "shell://script", "OFFLINE", (int) latency,
                    err.length() > 500 ? err.substring(0, 500) : err);
        }
        return result;
    }

    private void updateServiceStatus(InfraItem service, String status, Integer latency, String error) {
        Map<String, Object> extra = service.getExtra() != null ?
                new HashMap<>(service.getExtra()) : new HashMap<>();
        extra.put("status", status);
        extra.put("latencyMs", latency);
        extra.put("lastCheckTime", LocalDateTime.now().toString());
        extra.put("lastError", error);
        service.setExtra(extra);
        itemRepository.save(service);
    }

    private void saveLog(InfraItem service, String checkUrl, String status, Integer latency, String error) {
        InfraHealthLog log = new InfraHealthLog();
        log.setServiceId(service.getId());
        log.setServiceName(service.getName());
        log.setCheckUrl(checkUrl);
        log.setStatus(status);
        log.setLatencyMs(latency);
        log.setErrorMsg(error);
        log.setCheckedAt(LocalDateTime.now());
        healthLogRepository.save(log);
    }

    public Page<InfraHealthLog> getLogs(String serviceId, int page, int size) {
        return healthLogRepository.findByServiceIdOrderByCheckedAtDesc(
                serviceId, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "checkedAt")));
    }

    public List<InfraHealthLog> getRecentLogs(String serviceId, int limit) {
        return healthLogRepository.findTop10ByServiceIdOrderByCheckedAtDesc(serviceId);
    }
}
