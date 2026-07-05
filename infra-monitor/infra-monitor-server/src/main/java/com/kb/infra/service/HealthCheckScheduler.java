package com.kb.infra.service;

import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthCheckScheduler {

    private final InfraItemRepository itemRepository;
    private final HealthCheckService healthCheckService;

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void scheduledCheck() {
        List<InfraItem> services = itemRepository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc("service", 0);
        int count = 0;
        for (InfraItem service : services) {
            Object enabled = service.getExtra() != null ? service.getExtra().get("enabled") : null;
            if (enabled == null || (enabled instanceof Boolean && (Boolean) enabled) ||
                    (enabled instanceof Integer && (Integer) enabled == 1) ||
                    (enabled instanceof Number && ((Number) enabled).intValue() == 1)) {
                try {
                    healthCheckService.checkOne(service.getId());
                    count++;
                } catch (Exception e) {
                    log.warn("定时健康检查失败: {} - {}", service.getName(), e.getMessage());
                }
            }
        }
        log.debug("定时健康检查完成，检查了 {} 个服务", count);
    }
}
