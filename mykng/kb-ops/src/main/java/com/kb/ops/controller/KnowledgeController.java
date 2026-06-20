package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.dto.OpsKnowledgeRequest;
import com.kb.ops.entity.OpsKnowledge;
import com.kb.ops.service.OpsKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final OpsKnowledgeService knowledgeService;

    @GetMapping("/list")
    public Result<PageResult<OpsKnowledge>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(knowledgeService.list(keyword, category, hostId, serviceId, page, size));
    }

    @GetMapping("/{id}")
    public Result<OpsKnowledge> getById(@PathVariable Long id) {
        return Result.ok(knowledgeService.getById(id));
    }

    @PostMapping
    public Result<OpsKnowledge> create(@Valid @RequestBody OpsKnowledgeRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @PutMapping("/{id}")
    public Result<OpsKnowledge> update(@PathVariable Long id, @Valid @RequestBody OpsKnowledgeRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }
}
