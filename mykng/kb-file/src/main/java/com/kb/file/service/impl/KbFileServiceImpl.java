package com.kb.file.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.FileChunk;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.FileChunkMapper;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.EventPublisher;
import com.kb.file.service.FileParseTrigger;
import com.kb.file.service.KbFileService;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbFileServiceImpl implements KbFileService {

    private static final String BUCKET = "kb-file";
    private static final long PART_SIZE = 10 * 1024 * 1024L; // 10MB 分片大小

    private final KbFileMapper kbFileMapper;
    private final FileChunkMapper fileChunkMapper;
    private final MinioService minioService;
    private final FileParseTrigger fileParseTrigger;
    private final EventPublisher eventPublisher;
    private final SearchIndexService searchIndexService;
    private final FileContentRepository fileContentRepository;

    @Override
    public String uploadChunk(Long userId, String fileId, Integer chunkNumber, MultipartFile file) {
        // 简单上传模式（无 fileId/chunkNumber）
        if (fileId == null || fileId.isBlank()) {
            return simpleUpload(userId, file);
        }
        try {
            String objectName = "chunks/" + fileId + "/" + chunkNumber;
            minioService.upload(BUCKET, objectName, file);

            // 记录分片信息
            FileChunk chunk = new FileChunk();
            chunk.setFileId(fileId);
            chunk.setChunkNumber(chunkNumber);
            chunk.setChunkPath(objectName);
            fileChunkMapper.insert(chunk);

            return "chunk " + chunkNumber + " uploaded";
        } catch (Exception e) {
            throw new BusinessException("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 简单上传模式：直接上传整个文件到 MinIO，不经过分片流程
     */
    private String simpleUpload(Long userId, MultipartFile file) {
        try {
            String ext = FileUtil.extName(file.getOriginalFilename());
            String objectName = "files/" + IdUtil.fastSimpleUUID() + "." + ext;
            minioService.upload(BUCKET, objectName, file);

            KbFile kbFile = new KbFile();
            kbFile.setFolderId(0L);
            kbFile.setUserId(userId);
            kbFile.setName(file.getOriginalFilename());
            kbFile.setType(ext != null ? ext : "");
            kbFile.setSize(file.getSize());
            kbFile.setMinioPath(objectName);
            kbFile.setParseStatus("PENDING");
            kbFile.setStarred(0);
            kbFileMapper.insert(kbFile);

            fileParseTrigger.trigger(kbFile.getId(), kbFile.getMinioPath(), kbFile.getType());
            return String.valueOf(kbFile.getId());
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 合并分片（修复 OOM 问题）
     * <p>
     * 原代码使用 ByteArrayOutputStream 将所有分片加载到内存后再上传，
     * 大文件会导致 OOM。
     * <p>
     * 修复方案：使用 SequenceInputStream 惰性拼接各分片流，
     * 通过 MinIO multipart upload 流式上传，内存中仅保留一个分片的缓冲。
     */
    @Override
    @Transactional
    public KbFile mergeChunks(Long userId, FileMergeRequest request) {
        String ext = FileUtil.extName(request.getName());
        String objectName = "files/" + IdUtil.fastSimpleUUID() + "." + ext;
        int totalChunks = request.getTotalChunks() != null ? request.getTotalChunks() : 0;

        try {
            // 流式合并：惰性打开每个分片的下载流，通过 SequenceInputStream 顺序拼接
            // MinIO putObject 使用 multipart upload，按 PART_SIZE 分片上传，不会全量加载到内存
            long totalSize = request.getSize() != null ? request.getSize() : -1L;
            InputStream mergedStream = createChunkSequenceStream(request.getFileId(), totalChunks);
            minioService.uploadStream(BUCKET, objectName, mergedStream, totalSize, PART_SIZE, "application/octet-stream");

            // 合并成功后清理分片
            cleanupChunks(request.getFileId(), totalChunks);
            fileChunkMapper.delete(
                    new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileId, request.getFileId()));
        } catch (Exception e) {
            log.error("文件合并失败 fileId={}: {}", request.getFileId(), e.getMessage(), e);
            throw new BusinessException("文件合并失败: " + e.getMessage());
        }

        KbFile kbFile = new KbFile();
        kbFile.setFolderId(request.getFolderId());
        kbFile.setUserId(userId);
        kbFile.setName(request.getName());
        kbFile.setType(ext != null ? ext : "");
        kbFile.setSize(request.getSize() != null ? request.getSize() : 0L);
        kbFile.setMinioPath(objectName);
        kbFile.setParseStatus("PENDING");
        kbFile.setStarred(0);
        kbFileMapper.insert(kbFile);

        log.info("文件合并成功 fileId={} name={} userId={}", kbFile.getId(), request.getName(), userId);

        // 通过独立 Bean 触发异步解析（修复 @Async 自调用问题）
        fileParseTrigger.trigger(kbFile.getId(), kbFile.getMinioPath(), kbFile.getType());

        return kbFile;
    }

    @Override
    public PageResult<KbFile> list(Long userId, Long folderId, int page, int size) {
        Page<KbFile> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KbFile> wrapper = new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getUserId, userId)
                .eq(folderId != null, KbFile::getFolderId, folderId)
                .orderByDesc(KbFile::getCreatedAt);
        Page<KbFile> result = kbFileMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public KbFile getById(Long id, Long userId) {
        KbFile file = kbFileMapper.selectById(id);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException(404, "文件不存在");
        }
        return file;
    }

    @Override
    public String getParseStatus(Long id, Long userId) {
        KbFile file = getById(id, userId);
        return file.getParseStatus();
    }

    @Override
    public String getDownloadUrl(Long id, Long userId) {
        KbFile file = getById(id, userId);
        if (file.getMinioPath() == null) {
            throw new BusinessException("文件路径不存在");
        }
        return minioService.getPresignedUrl(BUCKET, file.getMinioPath(), 3600);
    }

    @Override
    public void reparse(Long id, Long userId) {
        KbFile file = getById(id, userId);
        file.setParseStatus("PENDING");
        file.setParseError(null);
        kbFileMapper.updateById(file);

        // 通过独立 Bean 触发异步解析（修复 @Async 自调用问题）
        fileParseTrigger.trigger(file.getId(), file.getMinioPath(), file.getType());
    }

    @Override
    public void delete(Long id, Long userId) {
        KbFile file = getById(id, userId);

        // 删除 MinIO 中的文件
        if (file.getMinioPath() != null) {
            minioService.remove(BUCKET, file.getMinioPath());
        }

        // 删除 MeiliSearch 索引
        searchIndexService.removeIndex(id);

        kbFileMapper.deleteById(id);

        // 发布文件删除事件（通知下游服务清理关联数据）
        eventPublisher.publishFileDeleted(id, userId);

        log.info("文件删除成功 fileId={} userId={}", id, userId);
    }

    @Override
    public void star(Long id, Long userId) {
        KbFile file = getById(id, userId);
        file.setStarred(file.getStarred() == 1 ? 0 : 1);
        kbFileMapper.updateById(file);
    }

    @Override
    public void move(Long id, Long userId, FileMoveRequest request) {
        KbFile file = getById(id, userId);
        file.setFolderId(request.getFolderId());
        kbFileMapper.updateById(file);
    }

    @Override
    public String getContent(Long id, Long userId) {
        KbFile file = getById(id, userId);
        return fileContentRepository.findByFileIdAndIsCurrentTrue(id)
                .map(c -> c.getContent() != null ? c.getContent() : "")
                .orElse("");
    }

    // ======================== 私有方法 ========================

    /**
     * 创建分片顺序拼接流
     * <p>
     * 使用 SequenceInputStream 惰性打开每个分片的 MinIO 下载流，
     * 当前分片读完后自动关闭并打开下一个，内存中仅保留一个分片的数据。
     */
    private InputStream createChunkSequenceStream(String fileId, int totalChunks) {
        Enumeration<InputStream> enumeration = new Enumeration<>() {
            private int current = 1;

            @Override
            public boolean hasMoreElements() {
                return current <= totalChunks;
            }

            @Override
            public InputStream nextElement() {
                String chunkPath = "chunks/" + fileId + "/" + current;
                current++;
                return minioService.download(BUCKET, chunkPath);
            }
        };
        return new java.io.SequenceInputStream(enumeration);
    }

    /**
     * 清理 MinIO 中的分片文件
     */
    private void cleanupChunks(String fileId, int totalChunks) {
        for (int i = 1; i <= totalChunks; i++) {
            minioService.remove(BUCKET, "chunks/" + fileId + "/" + i);
        }
    }
}
