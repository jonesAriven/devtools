package com.kb.intelligence.controller;

import com.kb.common.result.Result;
import com.kb.intelligence.dto.request.ImportByPathRequest;
import com.kb.intelligence.service.KnowledgeEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/intelligence/import")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final KnowledgeEngine knowledgeEngine;

    @PostMapping("/path")
    public Result<Map<String, Object>> importByPath(@RequestBody ImportByPathRequest request) {
        log.info("接收到导入请求: path={}, incremental={}", request.getPath(), request.getIncremental());
        long start = System.currentTimeMillis();

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath(
                request.getPath(),
                request.getIncremental() != null ? request.getIncremental() : true
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", request.getPath());
        result.put("totalFiles", stats.getTotalFiles());
        result.put("successFiles", stats.getSuccessFiles());
        result.put("failedFiles", stats.getFailedFiles());
        result.put("durationMs", stats.getDurationMs());
        result.put("durationSec", stats.getDurationMs() / 1000);
        log.info("导入完成: {} 文件, {} 成功, {} 失败, {}秒",
                stats.getTotalFiles(), stats.getSuccessFiles(), stats.getFailedFiles(), stats.getDurationMs() / 1000);
        return Result.ok(result);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "kb-intelligence");
        status.put("status", "running");
        return Result.ok(status);
    }
}
