package com.kb.knowledge.controller;

import com.kb.common.result.Result;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.service.FolderService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @GetMapping("/tree")
    public Result<List<Folder>> getTreeByParam(@RequestParam Long spaceId) {
        return Result.ok(folderService.getTree(spaceId, getCurrentUserId()));
    }

    @GetMapping("/tree/{spaceId}")
    public Result<List<Folder>> getTree(@PathVariable Long spaceId) {
        return Result.ok(folderService.getTree(spaceId, getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public Result<Folder> getById(@PathVariable Long id) {
        return Result.ok(folderService.getById(id, getCurrentUserId()));
    }

    @PostMapping
    public Result<Folder> create(@Valid @RequestBody FolderCreateRequest request) {
        return Result.ok(folderService.create(getCurrentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<Folder> update(@PathVariable Long id, @RequestBody FolderUpdateRequest request) {
        return Result.ok(folderService.update(id, getCurrentUserId(), request.getName()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody FolderMoveRequest request) {
        folderService.move(id, getCurrentUserId(), request);
        return Result.ok();
    }

    @PutMapping("/{id}/sort")
    public Result<Void> sort(@PathVariable Long id, @Valid @RequestBody FolderSortRequest request) {
        folderService.sort(id, getCurrentUserId(), request);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }

    @Data
    public static class FolderUpdateRequest {
        private String name;
    }
}
