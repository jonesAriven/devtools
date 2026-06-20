package com.kb.knowledge.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.knowledge.dto.doc.DocCreateRequest;
import com.kb.knowledge.dto.doc.DocMoveRequest;
import com.kb.knowledge.dto.doc.DocUpdateRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.service.DocService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @GetMapping("/list")
    public Result<PageResult<Doc>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(docService.list(getCurrentUserId(), folderId, page, size));
    }

    @PostMapping
    public Result<Doc> create(@Valid @RequestBody DocCreateRequest request) {
        return Result.ok(docService.create(getCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    public Result<Doc> getById(@PathVariable Long id) {
        return Result.ok(docService.getById(id, getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public Result<Doc> update(@PathVariable Long id, @RequestBody DocUpdateRequest request) {
        return Result.ok(docService.update(id, getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/star")
    public Result<Void> star(@PathVariable Long id) {
        docService.star(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody DocMoveRequest request) {
        docService.move(id, getCurrentUserId(), request);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
