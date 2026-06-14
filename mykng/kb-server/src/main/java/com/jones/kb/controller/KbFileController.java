package com.jones.kb.controller;

import com.jones.kb.common.PageResult;
import com.jones.kb.common.R;
import com.jones.kb.dto.file.FileMergeRequest;
import com.jones.kb.dto.file.FileMoveRequest;
import com.jones.kb.entity.KbFile;
import com.jones.kb.service.KbFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class KbFileController {

    private final KbFileService kbFileService;

    @PostMapping("/upload")
    public R<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileId", required = false) String fileId,
            @RequestParam(value = "chunkNumber", required = false) Integer chunkNumber) {
        return R.ok(kbFileService.uploadChunk(getCurrentUserId(), fileId, chunkNumber, file));
    }

    @PostMapping("/merge")
    public R<KbFile> merge(@Valid @RequestBody FileMergeRequest request) {
        return R.ok(kbFileService.mergeChunks(getCurrentUserId(), request));
    }

    @GetMapping("/list")
    public R<PageResult<KbFile>> list(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(kbFileService.list(getCurrentUserId(), folderId, page, size));
    }

    @GetMapping("/{id}")
    public R<KbFile> getById(@PathVariable Long id) {
        return R.ok(kbFileService.getById(id, getCurrentUserId()));
    }

    @GetMapping("/{id}/parse-status")
    public R<String> getParseStatus(@PathVariable Long id) {
        return R.ok(kbFileService.getParseStatus(id, getCurrentUserId()));
    }

    @GetMapping("/{id}/download")
    public R<String> download(@PathVariable Long id) {
        return R.ok(kbFileService.getDownloadUrl(id, getCurrentUserId()));
    }

    @PostMapping("/{id}/reparse")
    public R<Void> reparse(@PathVariable Long id) {
        kbFileService.reparse(id, getCurrentUserId());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        kbFileService.delete(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/star")
    public R<Void> star(@PathVariable Long id) {
        kbFileService.star(id, getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @Valid @RequestBody FileMoveRequest request) {
        kbFileService.move(id, getCurrentUserId(), request);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
