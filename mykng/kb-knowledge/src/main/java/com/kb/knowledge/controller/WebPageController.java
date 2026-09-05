package com.kb.knowledge.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.dto.web.WebCollectRequest;
import com.kb.knowledge.dto.web.WebMoveRequest;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.service.WebPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/web")
@RequiredArgsConstructor
public class WebPageController {

    private final WebPageService webPageService;

    @PostMapping("/collect")
    public Result<WebPage> collect(@Valid @RequestBody WebCollectRequest request) {
        return Result.ok(webPageService.collect(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public Result<PageResult<WebPage>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(webPageService.list(getCurrentUserId(), folderId, page, size));
    }

    @GetMapping("/{id}")
    public Result<WebPage> getById(@PathVariable Long id) {
        return Result.ok(webPageService.getById(id, getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        webPageService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/star")
    public Result<Void> star(@PathVariable Long id) {
        webPageService.star(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody WebMoveRequest request) {
        webPageService.move(id, getCurrentUserId(), request);
        return Result.ok();
    }

    @PostMapping("/{id}/refetch")
    public Result<WebPage> refetch(@PathVariable Long id) {
        return Result.ok(webPageService.refetch(id, getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
