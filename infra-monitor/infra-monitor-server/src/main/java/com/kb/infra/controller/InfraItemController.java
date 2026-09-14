package com.kb.infra.controller;

import com.marschat.auth.authz.RequirePermission;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.infra.dto.InfraItemRequest;
import com.kb.infra.entity.InfraItem;
import com.kb.infra.service.InfraItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 主机 / 配置 / 服务等条目（按 {@code type} 区分的通用端点）。
 *
 * <p><b>🔴 2026-09-15 安全修复（P1）：写操作补接口级鉴权。</b>
 * 此前增删改裸奔（只有认证），现按权限点闸门：
 * {@code marschat-inframon:api:items:create/update/delete}。读接口保持开放（看板/列表要渲染）。
 */
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class InfraItemController {

    private final InfraItemService service;

    @GetMapping("/list")
    public Result<PageResult<InfraItem>> list(
            @RequestParam String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(type, keyword, category, page, size));
    }

    @GetMapping("/all")
    public Result<List<InfraItem>> all(@RequestParam String type) {
        return Result.ok(service.listAll(type));
    }

    @GetMapping("/category/{type}/{category}")
    public Result<List<InfraItem>> byCategory(@PathVariable String type, @PathVariable String category) {
        return Result.ok(service.listByCategory(type, category));
    }

    @GetMapping("/{id}")
    public Result<InfraItem> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    @RequirePermission("api:items:create")
    @PostMapping
    public Result<InfraItem> create(@Valid @RequestBody InfraItemRequest request) {
        return Result.ok(service.create(request));
    }

    @RequirePermission("api:items:update")
    @PutMapping("/{id}")
    public Result<InfraItem> update(@PathVariable String id, @Valid @RequestBody InfraItemRequest request) {
        return Result.ok(service.update(id, request));
    }

    @RequirePermission("api:items:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    @GetMapping("/stats/{type}")
    public Result<Map<String, Long>> stats(@PathVariable String type) {
        return Result.ok(service.countByType(type));
    }
}
