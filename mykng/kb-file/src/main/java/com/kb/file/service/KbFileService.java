package com.kb.file.service;

import com.kb.common.page.PageResult;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.KbFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KbFileService {

    String uploadChunk(Long userId, String fileId, Integer chunkNumber, MultipartFile file);

    KbFile mergeChunks(Long userId, FileMergeRequest request);

    PageResult<KbFile> list(Long userId, Long folderId, int page, int size);

    KbFile getById(Long id, Long userId);

    String getParseStatus(Long id, Long userId);

    String getDownloadUrl(Long id, Long userId);

    /**
     * 流式下载文件（后端代理，避免暴露 MinIO 内部地址）
     *
     * @return InputStream 用于 Controller 包装为 ResponseEntity
     */
    java.io.InputStream downloadStream(Long id, Long userId);

    void reparse(Long id, Long userId);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);

    void move(Long id, Long userId, FileMoveRequest request);

    String getContent(Long id, Long userId);

    /**
     * 更新文本类文件内容（在线编辑）
     *
     * @param id      文件 ID
     * @param userId  当前用户 ID（仅文件所有者可编辑）
     * @param content 新内容字符串
     */
    void updateContent(Long id, Long userId, String content);

    /**
     * 查询用户所有文件（供跨服务资源树聚合使用）
     */
    List<KbFile> listAll(Long userId);

    List<KbFile> searchByName(String keyword, Long userId, Long folderId);
}
