package com.jones.kb.controller;

import com.jones.kb.common.PageResult;
import com.jones.kb.common.R;
import com.jones.kb.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(trashService.list(getCurrentUserId(), type, page, size));
    }

    @PostMapping("/restore/{type}/{id}")
    public R<Void> restore(@PathVariable String type, @PathVariable Long id) {
        trashService.restore(getCurrentUserId(), type, id);
        return R.ok();
    }

    @DeleteMapping("/{type}/{id}")
    public R<Void> permanentDelete(@PathVariable String type, @PathVariable Long id) {
        trashService.permanentDelete(getCurrentUserId(), type, id);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
