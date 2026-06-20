package com.kb.knowledge.controller;

import com.kb.common.result.Result;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/version")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @GetMapping("/list/{type}/{id}")
    public Result<List<Version>> listVersions(@PathVariable String type, @PathVariable Long id) {
        return Result.ok(versionService.listVersions(type, id));
    }

    @GetMapping("/{id}")
    public Result<Version> getVersion(@PathVariable Long id) {
        return Result.ok(versionService.getVersion(id));
    }

    @PostMapping("/{id}/rollback")
    public Result<Version> rollback(@PathVariable Long id) {
        return Result.ok(versionService.rollback(id, getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
