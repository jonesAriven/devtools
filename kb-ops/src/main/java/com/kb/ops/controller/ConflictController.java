package com.kb.ops.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.ops.entity.OpsConflict;
import com.kb.ops.service.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ops/conflict")
@RequiredArgsConstructor
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;

    /**
     * 执行一次矛盾检测
     */
    @PostMapping("/detect")
    public Result<Map<String, Integer>> detect() {
        int count = conflictDetectionService.detect();
        return Result.ok(Map.of("detected", count));
    }

    @GetMapping("/list")
    public Result<PageResult<OpsConflict>> list(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(conflictDetectionService.list(ruleCode, status, page, size));
    }

    /**
     * 标记矛盾为已解决
     */
    @PutMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id) {
        conflictDetectionService.resolve(id);
        return Result.ok();
    }
}
