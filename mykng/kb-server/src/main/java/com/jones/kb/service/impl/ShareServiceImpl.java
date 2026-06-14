package com.jones.kb.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jones.kb.common.BusinessException;
import com.jones.kb.common.PageResult;
import com.jones.kb.dto.share.ShareCreateRequest;
import com.jones.kb.entity.*;
import com.jones.kb.mapper.*;
import com.jones.kb.mongo.doc.DocContent;
import com.jones.kb.mongo.doc.FileContent;
import com.jones.kb.mongo.doc.WebContent;
import com.jones.kb.mongo.repository.DocContentRepository;
import com.jones.kb.mongo.repository.FileContentRepository;
import com.jones.kb.mongo.repository.WebContentRepository;
import com.jones.kb.service.MinioService;
import com.jones.kb.service.OperationLogService;
import com.jones.kb.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareMapper shareMapper;
    private final ShareAccessLogMapper shareAccessLogMapper;
    private final KbFileMapper kbFileMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final FolderMapper folderMapper;
    private final FileContentRepository fileContentRepository;
    private final DocContentRepository docContentRepository;
    private final WebContentRepository webContentRepository;
    private final MinioService minioService;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public Share create(Long userId, ShareCreateRequest request) {
        Share share = new Share();
        share.setUserId(userId);
        share.setResourceType(request.getResourceType());
        share.setResourceId(request.getResourceId());
        share.setCode(IdUtil.fastSimpleUUID());
        share.setExtractCode(request.getExtractCode() != null ? request.getExtractCode() : RandomUtil.randomNumbers(4));
        if (request.getExpireAt() != null && !request.getExpireAt().isBlank()) {
            share.setExpireAt(LocalDateTime.parse(request.getExpireAt()));
        }
        share.setViewCount(0);
        shareMapper.insert(share);

        operationLogService.log(userId, "SHARE", request.getResourceType(), request.getResourceId(), "创建分享", null);
        return share;
    }

    @Override
    public PageResult<Share> list(Long userId, int page, int size) {
        Page<Share> pageParam = new Page<>(page, size);
        Page<Share> result = shareMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Share>()
                        .eq(Share::getUserId, userId)
                        .orderByDesc(Share::getCreatedAt));
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public void delete(Long id, Long userId) {
        Share share = shareMapper.selectById(id);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new BusinessException("分享不存在");
        }
        shareMapper.deleteById(id);
    }

    @Override
    public Share verify(String code, String extractCode) {
        Share share = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>().eq(Share::getCode, code));
        if (share == null) {
            throw new BusinessException("分享不存在");
        }
        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("分享已过期");
        }
        if (share.getExtractCode() != null && !share.getExtractCode().equals(extractCode)) {
            throw new BusinessException("提取码错误");
        }

        share.setViewCount(share.getViewCount() + 1);
        shareMapper.updateById(share);

        return share;
    }

    @Override
    public Object getDetail(String code, String extractCode) {
        Share share = verify(code, extractCode);

        Map<String, Object> result = new HashMap<>();
        result.put("share", share);

        switch (share.getResourceType()) {
            case "file" -> {
                KbFile file = kbFileMapper.selectById(share.getResourceId());
                if (file != null) {
                    result.put("resource", file);
                    fileContentRepository.findByFileIdAndIsCurrentTrue(file.getId())
                            .ifPresent(fc -> result.put("content", fc));
                    if (file.getMinioPath() != null) {
                        result.put("downloadUrl", minioService.getPresignedUrl("kb-file", file.getMinioPath(), 3600));
                    }
                }
            }
            case "doc" -> {
                Doc doc = docMapper.selectById(share.getResourceId());
                if (doc != null) {
                    result.put("resource", doc);
                    docContentRepository.findByDocIdAndIsCurrentTrue(doc.getId())
                            .ifPresent(dc -> result.put("content", dc));
                }
            }
            case "web" -> {
                WebPage webPage = webPageMapper.selectById(share.getResourceId());
                if (webPage != null) {
                    result.put("resource", webPage);
                    webContentRepository.findByWebIdAndIsCurrentTrue(webPage.getId())
                            .ifPresent(wc -> result.put("content", wc));
                }
            }
            case "folder" -> {
                Folder folder = folderMapper.selectById(share.getResourceId());
                if (folder != null) {
                    result.put("resource", folder);
                }
            }
        }

        return result;
    }
}
