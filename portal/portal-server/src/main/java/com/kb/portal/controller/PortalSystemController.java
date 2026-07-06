package com.kb.portal.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.portal.dto.PortalSystemRequest;
import com.kb.portal.dto.SystemCredentials;
import com.kb.portal.entity.PortalSystem;
import com.kb.portal.service.PortalSystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class PortalSystemController {

    private final PortalSystemService portalSystemService;

    @GetMapping("/list")
    public Result<PageResult<PortalSystem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(portalSystemService.list(keyword, category, status, page, size));
    }

    @GetMapping("/all")
    public Result<List<PortalSystem>> all() {
        return Result.ok(portalSystemService.listAllEnabled());
    }

    @GetMapping("/category/{category}")
    public Result<List<PortalSystem>> listByCategory(@PathVariable String category) {
        return Result.ok(portalSystemService.listByCategory(category));
    }

    @GetMapping("/{id}")
    public Result<PortalSystem> getById(@PathVariable Long id) {
        return Result.ok(portalSystemService.getById(id));
    }

    @GetMapping("/{id}/credentials")
    public Result<SystemCredentials> getCredentials(@PathVariable Long id) {
        return Result.ok(portalSystemService.getCredentials(id));
    }

    @PostMapping
    public Result<PortalSystem> create(@Valid @RequestBody PortalSystemRequest request) {
        return Result.ok(portalSystemService.create(request));
    }

    @PutMapping("/{id}")
    public Result<PortalSystem> update(@PathVariable Long id, @Valid @RequestBody PortalSystemRequest request) {
        return Result.ok(portalSystemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        portalSystemService.delete(id);
        return Result.ok();
    }
}
