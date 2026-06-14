package com.jones.kb.controller;

import com.jones.kb.common.PageResult;
import com.jones.kb.common.R;
import com.jones.kb.dto.web.WebCollectRequest;
import com.jones.kb.entity.WebPage;
import com.jones.kb.service.WebPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebPageController {

    private final WebPageService webPageService;

    @PostMapping("/collect")
    public R<WebPage> collect(@Valid @RequestBody WebCollectRequest request) {
        return R.ok(webPageService.collect(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public R<PageResult<WebPage>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(webPageService.list(getCurrentUserId(), folderId, page, size));
    }

    @GetMapping("/{id}")
    public R<WebPage> getById(@PathVariable Long id) {
        return R.ok(webPageService.getById(id, getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        webPageService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/star")
    public R<Void> star(@PathVariable Long id) {
        webPageService.star(id, getCurrentUserId());
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
