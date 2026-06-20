package com.kb.file.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.KbFile;
import com.kb.file.service.KbFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class KbFileController {

    private final KbFileService kbFileService;

    @PostMapping("/upload")
    public Result<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestParam(value = "chunkNumber", required = false) Integer chunkNumber) {
        return Result.ok(kbFileService.uploadChunk(getCurrentUserId(), fileId, chunkNumber, file));
    }

    @PostMapping("/merge")
    public Result<KbFile> merge(@Valid @RequestBody FileMergeRequest request) {
        return Result.ok(kbFileService.mergeChunks(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public Result<PageResult<KbFile>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(kbFileService.list(getCurrentUserId(), folderId, page, size));
    }

    @GetMapping("/{id}")
    public Result<KbFile> getById(@PathVariable Long id) {
        return Result.ok(kbFileService.getById(id, getCurrentUserId()));
    }

    @GetMapping("/{id}/parse-status")
    public Result<String> getParseStatus(@PathVariable Long id) {
        return Result.ok(kbFileService.getParseStatus(id, getCurrentUserId()));
    }

    @GetMapping("/{id}/download")
    public Result<String> download(@PathVariable Long id) {
        return Result.ok(kbFileService.getDownloadUrl(id, getCurrentUserId()));
    }

    @PostMapping("/{id}/reparse")
    public Result<Void> reparse(@PathVariable Long id) {
        kbFileService.reparse(id, getCurrentUserId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbFileService.delete(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/star")
    public Result<Void> star(@PathVariable Long id) {
        kbFileService.star(id, getCurrentUserId());
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody FileMoveRequest request) {
        kbFileService.move(id, getCurrentUserId(), request);
        return Result.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
