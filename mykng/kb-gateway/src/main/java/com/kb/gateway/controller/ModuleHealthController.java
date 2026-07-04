package com.kb.gateway.controller;

import com.kb.gateway.dto.ModuleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 模块健康检查端点（M7-1 新增）
 * <p>
 * 通过 Nacos DiscoveryClient 获取所有已注册服务，返回模块状态。
 * 前端调用此端点动态显隐菜单，实现可拔插架构的"模块上下线自动感知"。
 * <p>
 * 端点：GET /kb/api/system/modules
 * <p>
 * 注意：Spring Cloud Gateway 是 WebFlux 应用，@RestController 的 RequestMappingHandlerMapping
 * 优先级高于 Gateway 的 RoutePredicateHandlerMapping，因此该端点不会被 Gateway 路由拦截。
 * 返回 Mono 以保持 Reactive 风格。
 */
@Slf4j
@RestController
@RequestMapping("/kb/api/system")
@RequiredArgsConstructor
public class ModuleHealthController {

    /** 已知的模块列表（与 module-registry.yml 保持一致） */
    private static final List<String> KNOWN_MODULES = List.of(
            "kb-gateway", "kb-auth", "kb-file", "kb-knowledge", "kb-intelligence"
    );

    private final DiscoveryClient discoveryClient;

    /**
     * 获取所有模块状态
     * <p>
     * 返回已知模块的在线状态和实例数。模块下线时 status=DOWN，前端据此隐藏菜单。
     */
    @GetMapping("/modules")
    public Mono<List<ModuleStatus>> listModules() {
        List<ModuleStatus> modules = new ArrayList<>();

        for (String moduleName : KNOWN_MODULES) {
            try {
                List<ServiceInstance> instances = discoveryClient.getInstances(moduleName);
                int count = instances != null ? instances.size() : 0;
                String status = count > 0 ? "UP" : "DOWN";
                modules.add(ModuleStatus.builder()
                        .name(moduleName)
                        .status(status)
                        .instances(count)
                        .available(count > 0)
                        .build());
            } catch (Exception e) {
                log.warn("获取模块 {} 状态失败: {}", moduleName, e.getMessage());
                modules.add(ModuleStatus.builder()
                        .name(moduleName)
                        .status("UNKNOWN")
                        .instances(0)
                        .available(false)
                        .build());
            }
        }

        // 按模块名排序，保证返回顺序稳定
        modules.sort(Comparator.comparing(ModuleStatus::getName));
        return Mono.just(modules);
    }

    /**
     * 获取单个模块状态
     */
    @GetMapping("/modules/{name}")
    public Mono<ModuleStatus> getModule(String name) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(name);
            int count = instances != null ? instances.size() : 0;
            String status = count > 0 ? "UP" : "DOWN";
            return Mono.just(ModuleStatus.builder()
                    .name(name)
                    .status(status)
                    .instances(count)
                    .available(count > 0)
                    .build());
        } catch (Exception e) {
            log.warn("获取模块 {} 状态失败: {}", name, e.getMessage());
            return Mono.just(ModuleStatus.builder()
                    .name(name)
                    .status("UNKNOWN")
                    .instances(0)
                    .available(false)
                    .build());
        }
    }
}
