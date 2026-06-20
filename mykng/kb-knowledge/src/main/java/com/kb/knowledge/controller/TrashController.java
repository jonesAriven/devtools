package com.kb.knowledge.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.knowledge.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(trashService.list(getCurrentUserId(), type, page, size));
    }

    @PostMapping("/restore/{type}/{id}")
    public Result<Void> restore(@PathVariable String type, @PathVariable Long id) {
        trashService.restore(getCurrentUserId(), type, id);
        return Result.ok();
    }

    @DeleteMapping("/{type}/{id}")
    public Result<Void> permanentDelete(@PathVariable String type, @PathVariable Long id) {
        trashService.permanentDelete(getCurrentUserId(), type, id);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
