package com.kb.infra.controller;

import com.kb.common.result.Result;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import com.kb.infra.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final InfraItemRepository itemRepository;
    private final HealthCheckService healthCheckService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> result = new HashMap<>();

        result.put("hostCount", itemRepository.countByTypeAndDeleted("host", 0));
        result.put("credentialCount", itemRepository.countByTypeAndDeleted("credential", 0));
        result.put("configCount", itemRepository.countByTypeAndDeleted("config", 0));
        result.put("serviceCount", itemRepository.countByTypeAndDeleted("service", 0));

        List<InfraItem> services = itemRepository.findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc("service", 0);
        long online = 0, offline = 0, unknown = 0;
        for (InfraItem s : services) {
            String status = s.getExtra() != null ? (String) s.getExtra().get("status") : null;
            if ("ONLINE".equals(status)) online++;
            else if ("OFFLINE".equals(status)) offline++;
            else unknown++;
        }
        result.put("servicesOnline", online);
        result.put("servicesOffline", offline);
        result.put("servicesUnknown", unknown);

        return Result.ok(result);
    }
}
