package com.kb.infra.controller;

import com.kb.common.result.Result;
import com.kb.infra.entity.InfraHealthLog;
import com.kb.infra.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    @PostMapping("/check-all")
    public Result<Map<String, Object>> checkAll() {
        return Result.ok(healthCheckService.checkAll());
    }

    @PostMapping("/check/{serviceId}")
    public Result<Map<String, Object>> checkOne(@PathVariable String serviceId) {
        return Result.ok(healthCheckService.checkOne(serviceId));
    }

    @GetMapping("/logs/{serviceId}")
    public Result<Page<InfraHealthLog>> logs(
            @PathVariable String serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(healthCheckService.getLogs(serviceId, page, size));
    }

    @GetMapping("/logs/{serviceId}/recent")
    public Result<java.util.List<InfraHealthLog>> recentLogs(@PathVariable String serviceId) {
        return Result.ok(healthCheckService.getRecentLogs(serviceId, 10));
    }
}
