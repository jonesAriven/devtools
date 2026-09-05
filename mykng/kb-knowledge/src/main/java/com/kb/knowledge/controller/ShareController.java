package com.kb.knowledge.controller;

import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.Share;
import com.kb.knowledge.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    public Result<Share> create(@Valid @RequestBody ShareCreateRequest request) {
        return Result.ok(shareService.create(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public Result<PageResult<Share>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(shareService.list(getCurrentUserId(), page, size));
    }

    @GetMapping("/my")
    public Result<List<Share>> getMyShares() {
        return Result.ok(shareService.listMyShares(getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        shareService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @GetMapping("/verify/{code}")
    public Result<Share> verify(@PathVariable String code,
                                @RequestParam(required = false) String extractCode) {
        return Result.ok(shareService.verify(code, extractCode));
    }

    @GetMapping("/detail/{code}")
    public Result<Object> getDetail(@PathVariable String code,
                                    @RequestParam(required = false) String extractCode) {
        return Result.ok(shareService.getDetail(code, extractCode));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return Long.parseLong(auth.getName());
    }
}
