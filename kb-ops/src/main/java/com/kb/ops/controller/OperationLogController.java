package com.kb.ops.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.ops.entity.OperationLog;
import com.kb.ops.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService logService;

    @GetMapping("/list")
    public Result<PageResult<OperationLog>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(logService.list(userId, action, resourceType, startTime, endTime, page, size));
    }

    @GetMapping("/{id}")
    public Result<OperationLog> detail(@PathVariable Long id) {
        return Result.ok(logService.getById(id));
    }
}
