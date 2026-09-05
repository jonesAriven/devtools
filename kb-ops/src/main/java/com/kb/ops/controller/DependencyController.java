package com.kb.ops.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.ops.dto.DependencyRequest;
import com.kb.ops.entity.Dependency;
import com.kb.ops.service.DependencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/dependency")
@RequiredArgsConstructor
public class DependencyController {

    private final DependencyService dependencyService;

    @GetMapping("/list")
    public Result<PageResult<Dependency>> list(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(dependencyService.list(serviceId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Dependency> getById(@PathVariable Long id) {
        return Result.ok(dependencyService.getById(id));
    }

    @PostMapping
    public Result<Dependency> create(@Valid @RequestBody DependencyRequest request) {
        return Result.ok(dependencyService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Dependency> update(@PathVariable Long id, @Valid @RequestBody DependencyRequest request) {
        return Result.ok(dependencyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dependencyService.delete(id);
        return Result.ok();
    }
}
