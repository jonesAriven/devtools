package com.kb.ops.controller;

import com.kb.common.result.Result;
import com.kb.ops.dto.SyncFromIntelRequest;
import com.kb.ops.dto.SyncFromIntelResult;
import com.kb.ops.service.SyncFromIntelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ops/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncFromIntelService syncFromIntelService;

    @PostMapping("/from-intelligence")
    public Result<SyncFromIntelResult> syncFromIntelligence(@RequestBody(required = false) SyncFromIntelRequest request) {
        if (request == null) request = new SyncFromIntelRequest();
        log.info("[知识引擎同步] 开始同步，override={}, entityTypes={}", request.isOverride(), request.getEntityTypes());
        SyncFromIntelResult result = syncFromIntelService.syncFromIntelligence(request);
        return result.getError() != null ? Result.fail(500, result.getError()) : Result.ok(result);
    }
}
