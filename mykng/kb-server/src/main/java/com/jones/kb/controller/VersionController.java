package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.entity.Version;
import com.jones.kb.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @GetMapping("/list/{type}/{id}")
    public R<List<Version>> listVersions(@PathVariable String type, @PathVariable Long id) {
        return R.ok(versionService.listVersions(type, id));
    }

    @GetMapping("/{id}")
    public R<Version> getVersion(@PathVariable Long id) {
        return R.ok(versionService.getVersion(id));
    }

    @PostMapping("/{id}/rollback")
    public R<Version> rollback(@PathVariable Long id) {
        return R.ok(versionService.rollback(id, getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
