package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.dto.ServiceRequest;
import com.kb.ops.entity.OpsService;
import com.kb.ops.service.OpsServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/service")
@RequiredArgsConstructor
public class ServiceController {

    private final OpsServiceService serviceService;

    @GetMapping("/list")
    public Result<PageResult<OpsService>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(serviceService.list(keyword, hostId, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<OpsService> getById(@PathVariable Long id) {
        return Result.ok(serviceService.getById(id));
    }

    @PostMapping
    public Result<OpsService> create(@Valid @RequestBody ServiceRequest request) {
        return Result.ok(serviceService.create(request));
    }

    @PutMapping("/{id}")
    public Result<OpsService> update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return Result.ok(serviceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        serviceService.delete(id);
        return Result.ok();
    }
}
