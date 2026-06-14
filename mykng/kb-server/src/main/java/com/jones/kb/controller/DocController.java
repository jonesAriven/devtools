package com.jones.kb.controller;

import com.jones.kb.common.PageResult;
import com.jones.kb.common.R;
import com.jones.kb.dto.doc.DocCreateRequest;
import com.jones.kb.dto.doc.DocMoveRequest;
import com.jones.kb.dto.doc.DocUpdateRequest;
import com.jones.kb.entity.Doc;
import com.jones.kb.service.DocService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @GetMapping("/list")
    public R<PageResult<Doc>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(docService.list(getCurrentUserId(), folderId, page, size));
    }

    @PostMapping
    public R<Doc> create(@Valid @RequestBody DocCreateRequest request) {
        return R.ok(docService.create(getCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    public R<Doc> getById(@PathVariable Long id) {
        return R.ok(docService.getById(id, getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public R<Doc> update(@PathVariable Long id, @RequestBody DocUpdateRequest request) {
        return R.ok(docService.update(id, getCurrentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        docService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/star")
    public R<Void> star(@PathVariable Long id) {
        docService.star(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @Valid @RequestBody DocMoveRequest request) {
        docService.move(id, getCurrentUserId(), request);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
