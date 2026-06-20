package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.dto.HostRequest;
import com.kb.ops.entity.Host;
import com.kb.ops.service.HostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/host")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;

    @GetMapping("/list")
    public Result<PageResult<Host>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(hostService.list(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Host> getById(@PathVariable Long id,
                                @RequestParam(defaultValue = "false") boolean revealPassword) {
        return Result.ok(hostService.getById(id, revealPassword));
    }

    @PostMapping
    public Result<Host> create(@Valid @RequestBody HostRequest request) {
        return Result.ok(hostService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Host> update(@PathVariable Long id, @Valid @RequestBody HostRequest request) {
        return Result.ok(hostService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        hostService.delete(id);
        return Result.ok();
    }
}
