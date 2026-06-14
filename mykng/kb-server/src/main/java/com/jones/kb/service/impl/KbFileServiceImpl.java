package com.jones.kb.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jones.kb.common.BusinessException;
import com.jones.kb.common.PageResult;
import com.jones.kb.dto.file.FileMergeRequest;
import com.jones.kb.dto.file.FileMoveRequest;
import com.jones.kb.entity.FileChunk;
import com.jones.kb.entity.KbFile;
import com.jones.kb.mapper.FileChunkMapper;
import com.jones.kb.mapper.KbFileMapper;
import com.jones.kb.service.FileParseService;
import com.jones.kb.service.KbFileService;
import com.jones.kb.service.MinioService;
import com.jones.kb.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KbFileServiceImpl implements KbFileService {

    private final KbFileMapper kbFileMapper;
    private final FileChunkMapper fileChunkMapper;
    private final MinioService minioService;
    private final FileParseService fileParseService;
    private final OperationLogService operationLogService;

    @Override
    public String uploadChunk(Long userId, String fileId, Integer chunkNumber, MultipartFile file) {
        try {
            String objectName = "chunks/" + fileId + "/" + chunkNumber;
            minioService.upload("kb-file", objectName, file);
            return "chunk " + chunkNumber + " uploaded";
        } catch (Exception e) {
            throw new BusinessException("分片上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public KbFile mergeChunks(Long userId, FileMergeRequest request) {
        String ext = FileUtil.extName(request.getName());
        String objectName = "files/" + IdUtil.fastSimpleUUID() + "." + ext;

        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            for (int i = 1; i <= request.getTotalChunks(); i++) {
                String chunkPath = "chunks/" + request.getFileId() + "/" + i;
                java.io.InputStream is = minioService.download("kb-file", chunkPath);
                is.transferTo(baos);
                is.close();
                minioService.remove("kb-file", chunkPath);
            }

            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(baos.toByteArray());
            minioService.upload("kb-file", objectName, bais, bais.available(), "application/octet-stream");

            fileChunkMapper.delete(
                    new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileId, request.getFileId()));
        } catch (Exception e) {
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

        operationLogService.log(userId, "UPLOAD", "file", kbFile.getId(), "上传文件: " + request.getName(), null);

        triggerAsyncParse(kbFile);

        return kbFile;
    }

    @Async("kbAsyncExecutor")
    public void triggerAsyncParse(KbFile kbFile) {
        fileParseService.parseFile(kbFile.getId(), kbFile.getMinioPath(), kbFile.getType());
    }

    @Override
    public PageResult<KbFile> list(Long userId, Long folderId, int page, int size) {
        Page<KbFile> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<KbFile> wrapper = new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getUserId, userId)
                .eq(folderId != null, KbFile::getFolderId, folderId)
                .orderByDesc(KbFile::getCreatedAt);
        Page<KbFile> result = kbFileMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public KbFile getById(Long id, Long userId) {
        KbFile file = kbFileMapper.selectById(id);
        if (file == null || !file.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在");
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
        return minioService.getPresignedUrl("kb-file", file.getMinioPath(), 3600);
    }

    @Override
    public void reparse(Long id, Long userId) {
        KbFile file = getById(id, userId);
        file.setParseStatus("PENDING");
        file.setParseError(null);
        kbFileMapper.updateById(file);
        triggerAsyncParse(file);
    }

    @Override
    public void delete(Long id, Long userId) {
        KbFile file = getById(id, userId);
        kbFileMapper.deleteById(id);
        operationLogService.log(userId, "DELETE", "file", id, "删除文件: " + file.getName(), null);
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
}
