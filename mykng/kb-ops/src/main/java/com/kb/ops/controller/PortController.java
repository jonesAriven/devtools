package com.kb.ops.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.ops.dto.PortRequest;
import com.kb.ops.entity.Port;
import com.kb.ops.service.PortService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops/port")
@RequiredArgsConstructor
public class PortController {

    private final PortService portService;

    @GetMapping("/list")
    public Result<PageResult<Port>> list(
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(portService.list(hostId, serviceId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Port> getById(@PathVariable Long id) {
        return Result.ok(portService.getById(id));
    }

    @PostMapping
    public Result<Port> create(@Valid @RequestBody PortRequest request) {
        return Result.ok(portService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Port> update(@PathVariable Long id, @Valid @RequestBody PortRequest request) {
        return Result.ok(portService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        portService.delete(id);
        return Result.ok();
    }
}
