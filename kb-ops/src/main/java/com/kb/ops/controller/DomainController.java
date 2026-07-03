package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.dto.DomainRequest;
import com.kb.ops.entity.Domain;
import com.kb.ops.service.DomainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/domain")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @GetMapping("/list")
    public Result<PageResult<Domain>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(domainService.list(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Domain> getById(@PathVariable Long id) {
        return Result.ok(domainService.getById(id));
    }

    @PostMapping
    public Result<Domain> create(@Valid @RequestBody DomainRequest request) {
        return Result.ok(domainService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Domain> update(@PathVariable Long id, @Valid @RequestBody DomainRequest request) {
        return Result.ok(domainService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        domainService.delete(id);
        return Result.ok();
    }
}
