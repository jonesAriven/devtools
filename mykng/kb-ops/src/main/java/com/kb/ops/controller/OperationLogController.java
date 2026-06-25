package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.entity.OperationLog;
import com.kb.ops.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService logService;

    @GetMapping("/list")
    public Result<PageResult<OperationLog>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(logService.list(userId, action, page, size));
    }
}
