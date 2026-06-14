package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.dto.tag.TagBindRequest;
import com.jones.kb.entity.Tag;
import com.jones.kb.service.TagService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    public R<List<Tag>> list() {
        return R.ok(tagService.listByUserId(getCurrentUserId()));
    }

    @PostMapping
    public R<Tag> create(@RequestBody TagCreateRequest request) {
        return R.ok(tagService.create(getCurrentUserId(), request.getName(), request.getColor()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tagService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @PostMapping("/bind")
    public R<Void> bind(@Valid @RequestBody TagBindRequest request) {
        tagService.bind(getCurrentUserId(), request);
        return R.ok();
    }

    @DeleteMapping("/unbind")
    public R<Void> unbind(@RequestParam Long tagId,
                          @RequestParam String resourceType,
                          @RequestParam Long resourceId) {
        tagService.unbind(getCurrentUserId(), tagId, resourceType, resourceId);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }

    @Data
    public static class TagCreateRequest {
        private String name;
        private String color;
    }
}
