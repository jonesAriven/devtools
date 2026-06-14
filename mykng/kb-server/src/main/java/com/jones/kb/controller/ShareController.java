package com.jones.kb.controller;

import com.jones.kb.common.PageResult;
import com.jones.kb.common.R;
import com.jones.kb.dto.share.ShareCreateRequest;
import com.jones.kb.entity.Share;
import com.jones.kb.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public R<Share> create(@Valid @RequestBody ShareCreateRequest request) {
        return R.ok(shareService.create(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public R<PageResult<Share>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(shareService.list(getCurrentUserId(), page, size));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        shareService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @GetMapping("/verify/{code}")
    public R<Share> verify(@PathVariable String code,
                           @RequestParam(required = false) String extractCode) {
        return R.ok(shareService.verify(code, extractCode));
    }

    @GetMapping("/detail/{code}")
    public R<Object> getDetail(@PathVariable String code,
                               @RequestParam(required = false) String extractCode) {
        return R.ok(shareService.getDetail(code, extractCode));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
