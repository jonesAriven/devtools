package com.kb.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.*;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.*;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareMapper shareMapper;
    private final ShareAccessLogMapper shareAccessLogMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final FolderMapper folderMapper;
    private final DocContentRepository docContentRepository;
    private final WebContentRepository webContentRepository;
    private final FileClient fileClient;
    private final EventPublisher eventPublisher;

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
            // 兼容两种常见格式：ISO(2026-12-31T23:59:59) 和 标准(2026-12-31 23:59:59)
            String expireAtStr = request.getExpireAt().replace(" ", "T");
            share.setExpireAt(LocalDateTime.parse(expireAtStr));
        }
        share.setViewCount(0);
        shareMapper.insert(share);

        // 发布操作事件（替代 OperationLogService）
        eventPublisher.publishKnowledgeEvent(userId, "SHARE", request.getResourceType(),
                request.getResourceId(), "创建分享");
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
    public List<Share> listMyShares(Long userId) {
        return shareMapper.selectList(
                new LambdaQueryWrapper<Share>()
                        .eq(Share::getUserId, userId)
                        .orderByDesc(Share::getCreatedAt));
    }

    @Override
    public void delete(Long id, Long userId) {
        Share share = shareMapper.selectById(id);
        if (share == null || !share.getUserId().equals(userId)) {
            throw new BusinessException("分享不存在");
        }
        shareMapper.deleteById(id);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "share", id, "删除分享");
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

        shareMapper.update(null, new LambdaUpdateWrapper<Share>()
                .eq(Share::getId, share.getId())
                .setSql("view_count = view_count + 1"));

        return share;
    }

    @Override
    public Object getDetail(String code, String extractCode) {
        Share share = verify(code, extractCode);

        Map<String, Object> result = new HashMap<>();
        result.put("share", share);

        switch (share.getResourceType()) {
            case "file" -> {
                // 通过 Feign 调用 kb-file 获取文件信息和下载链接
                try {
                    Result<FileDTO> fileResult = fileClient.getById(share.getResourceId());
                    if (fileResult != null && fileResult.getCode() == 200 && fileResult.getData() != null) {
                        result.put("resource", fileResult.getData());
                    }
                    Result<String> downloadResult = fileClient.getDownloadUrl(share.getResourceId());
                    if (downloadResult != null && downloadResult.getCode() == 200 && downloadResult.getData() != null) {
                        result.put("downloadUrl", downloadResult.getData());
                    }
                    // 获取文件解析内容
                    Result<String> contentResult = fileClient.getContent(share.getResourceId());
                    if (contentResult != null && contentResult.getCode() == 200 && contentResult.getData() != null) {
                        Map<String, Object> content = new HashMap<>();
                        content.put("content", contentResult.getData());
                        result.put("content", content);
                    }
                } catch (Exception e) {
                    log.warn("Feign 获取分享文件详情失败 fileId={}: {}", share.getResourceId(), e.getMessage());
                }
            }
            case "doc" -> {
                Doc doc = docMapper.selectById(share.getResourceId());
                if (doc == null) {
                    throw new BusinessException("分享的资源已被删除");
                }
                result.put("resource", doc);
                docContentRepository.findByDocIdAndIsCurrentTrue(doc.getId())
                        .ifPresent(dc -> result.put("content", dc));
            }
            case "web" -> {
                WebPage webPage = webPageMapper.selectById(share.getResourceId());
                if (webPage == null) {
                    throw new BusinessException("分享的资源已被删除");
                }
                result.put("resource", webPage);
                webContentRepository.findByWebIdAndIsCurrentTrue(webPage.getId())
                        .ifPresent(wc -> result.put("content", wc));
            }
            case "folder" -> {
                Folder folder = folderMapper.selectById(share.getResourceId());
                if (folder == null) {
                    throw new BusinessException("分享的资源已被删除");
                }
                result.put("resource", folder);
            }
        }

        return result;
    }
}
