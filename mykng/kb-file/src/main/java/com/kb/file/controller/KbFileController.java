package com.kb.file.controller;

import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.KbFile;
import com.kb.file.service.KbFileService;
import com.kb.file.service.SearchIndexService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class KbFileController {

    private final KbFileService kbFileService;
    private final SearchIndexService searchIndexService;

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

    /**
     * 流式下载文件（后端代理，避免暴露 MinIO 内部地址）。
     * <p>
     * 前端应优先使用此接口下载文件，而非使用 presigned URL（presigned URL 的 host
     * 是容器内地址 minio:9000，浏览器无法访问）。
     * <p>
     * 使用 HttpServletResponse 直接写入流，避免 InputStreamResource 被 Spring 重复读取。
     */
    @GetMapping("/{id}/download-stream")
    public void downloadStream(@PathVariable Long id, HttpServletResponse response) {
        KbFile file = kbFileService.getById(id, getCurrentUserId());
        String encodedName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        if (file.getSize() != null && file.getSize() > 0) {
            response.setContentLengthLong(file.getSize());
        }
        try (InputStream stream = kbFileService.downloadStream(id, getCurrentUserId())) {
            stream.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("流式下载文件失败 fileId={}", id, e);
            throw new RuntimeException("下载文件失败", e);
        }
    }

    @GetMapping("/{id}/content")
    public Result<String> getContent(@PathVariable Long id) {
        return Result.ok(kbFileService.getContent(id, getCurrentUserId()));
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

    @GetMapping("/search")
    public Result<List<KbFile>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long folderId) {
        return Result.ok(kbFileService.searchByName(keyword, getCurrentUserId(), folderId));
    }

    @PostMapping("/rebuild-index")
    public Result<Integer> rebuildIndex() {
        log.info("开始全量重建文件索引");
        int count = searchIndexService.rebuildAllIndexes();
        return Result.ok(count);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
