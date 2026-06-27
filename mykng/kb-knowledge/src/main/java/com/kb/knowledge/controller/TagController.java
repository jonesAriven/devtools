package com.kb.knowledge.controller;

import com.kb.common.result.Result;
import com.kb.knowledge.dto.tag.TagBindRequest;
import com.kb.knowledge.entity.Tag;
import com.kb.knowledge.service.TagService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    public Result<List<Tag>> list() {
        return Result.ok(tagService.listByUserId(getCurrentUserId()));
    }

    @PostMapping
    public Result<Tag> create(@RequestBody TagCreateRequest request) {
        return Result.ok(tagService.create(getCurrentUserId(), request.getName(), request.getColor()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @PostMapping("/bind")
    public Result<Void> bind(@Valid @RequestBody TagBindRequest request) {
        tagService.bind(getCurrentUserId(), request);
        return Result.ok();
    }

    @DeleteMapping("/unbind")
    public Result<Void> unbind(@RequestParam Long tagId,
                               @RequestParam String resourceType,
                               @RequestParam Long resourceId) {
        tagService.unbind(getCurrentUserId(), tagId, resourceType, resourceId);
        return Result.ok();
    }

    @GetMapping("/resource")
    public Result<List<Tag>> getResourceTags(@RequestParam Long resourceId,
                                             @RequestParam String resourceType) {
        return Result.ok(tagService.getTagsByResource(getCurrentUserId(), resourceId, resourceType));
    }

    @PostMapping("/resource")
    public Result<Void> addResourceTag(@Valid @RequestBody TagBindRequest request) {
        tagService.bind(getCurrentUserId(), request);
        return Result.ok();
    }

    @DeleteMapping("/resource")
    public Result<Void> removeResourceTag(@RequestBody TagBindRequest request) {
        tagService.unbind(getCurrentUserId(), request.getTagId(), request.getResourceType(), request.getResourceId());
        return Result.ok();
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
