package com.jones.kb.controller;

import com.jones.kb.common.R;
import com.jones.kb.dto.folder.FolderCreateRequest;
import com.jones.kb.dto.folder.FolderMoveRequest;
import com.jones.kb.dto.folder.FolderSortRequest;
import com.jones.kb.entity.Folder;
import com.jones.kb.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping("/tree/{spaceId}")
    public R<List<Folder>> getTree(@PathVariable Long spaceId) {
        return R.ok(folderService.getTree(spaceId, getCurrentUserId()));
    }

    @PostMapping
    public R<Folder> create(@Valid @RequestBody FolderCreateRequest request) {
        return R.ok(folderService.create(getCurrentUserId(), request));
    }

    @PutMapping("/{id}")
    public R<Folder> update(@PathVariable Long id, @RequestBody FolderUpdateRequest request) {
        return R.ok(folderService.update(id, getCurrentUserId(), request.getName()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        folderService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @Valid @RequestBody FolderMoveRequest request) {
        folderService.move(id, getCurrentUserId(), request);
        return R.ok();
    }

    @PutMapping("/{id}/sort")
    public R<Void> sort(@PathVariable Long id, @Valid @RequestBody FolderSortRequest request) {
        folderService.sort(id, getCurrentUserId(), request);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }

    @lombok.Data
    public static class FolderUpdateRequest {
        private String name;
    }
}
