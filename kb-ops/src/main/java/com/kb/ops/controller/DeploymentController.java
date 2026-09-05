package com.kb.ops.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.ops.dto.DeploymentRecordRequest;
import com.kb.ops.entity.DeploymentRecord;
import com.kb.ops.service.DeploymentRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/deployment")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentRecordService deploymentRecordService;

    @GetMapping("/list")
    public Result<PageResult<DeploymentRecord>> list(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(deploymentRecordService.list(serviceId, page, size));
    }

    @GetMapping("/recent")
    public Result<java.util.List<DeploymentRecord>> recent(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(deploymentRecordService.recent(limit));
    }

    @PostMapping
    public Result<DeploymentRecord> create(@Valid @RequestBody DeploymentRecordRequest request) {
        return Result.ok(deploymentRecordService.create(request));
    }
}
