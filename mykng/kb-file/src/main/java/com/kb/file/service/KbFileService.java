package com.kb.file.service;

import com.kb.common.page.PageResult;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.KbFile;
import org.springframework.web.multipart.MultipartFile;

public interface KbFileService {

    String uploadChunk(Long userId, String fileId, Integer chunkNumber, MultipartFile file);

    KbFile mergeChunks(Long userId, FileMergeRequest request);

    PageResult<KbFile> list(Long userId, Long folderId, int page, int size);

    KbFile getById(Long id, Long userId);

    String getParseStatus(Long id, Long userId);

    String getDownloadUrl(Long id, Long userId);

    void reparse(Long id, Long userId);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);

    void move(Long id, Long userId, FileMoveRequest request);
}
