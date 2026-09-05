package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.page.PageResult;
import com.kb.knowledge.dto.doc.DocCreateRequest;
import com.kb.knowledge.dto.doc.DocMoveRequest;
import com.kb.knowledge.dto.doc.DocUpdateRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.service.DocService;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private final DocMapper docMapper;
    private final VersionMapper versionMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final com.kb.knowledge.mapper.FolderMapper folderMapper;
    private final DocContentRepository docContentRepository;
    private final EventPublisher eventPublisher;
    private final SearchIndexService searchIndexService;

    @Override
    public PageResult<Doc> list(Long userId, Long folderId, int page, int size) {
        Page<Doc> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Doc> wrapper = new LambdaQueryWrapper<Doc>()
                .eq(Doc::getUserId, userId)
                .eq(folderId != null, Doc::getFolderId, folderId)
                .orderByDesc(Doc::getUpdatedAt);
        Page<Doc> result = docMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public Doc create(Long userId, DocCreateRequest request) {
        Doc doc = new Doc();
        doc.setFolderId(request.getFolderId());
        doc.setUserId(userId);
        doc.setTitle(request.getTitle());
        // 文档格式：默认 html，支持 markdown
        String format = request.getFormat();
        doc.setFormat(format != null && !format.isBlank() ? format : "html");
        doc.setStarred(0);
        docMapper.insert(doc);

        DocContent content = new DocContent();
        content.setDocId(doc.getId());
        content.setUserId(userId);
        content.setContent(request.getContent() != null ? request.getContent() : "");
        content.setVersion(1);
        content.setIsCurrent(true);
        content.setCreatedAt(LocalDateTime.now());
        docContentRepository.save(content);

        Version version = new Version();
        version.setResourceType("doc");
        version.setResourceId(doc.getId());
        version.setVersionNum(1);
        versionMapper.insert(version);

        // 写入 MeiliSearch 索引（事务提交后执行，避免事务回滚但索引已写入的不一致）
        final String indexContent = request.getContent();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                searchIndexService.indexDoc(doc, indexContent);
            }
        });

        // 发布操作事件（替代 OperationLogService）
        eventPublisher.publishKnowledgeEvent(userId, "CREATE", "doc", doc.getId(),
                "创建文档: " + request.getTitle());
        return doc;
    }

    @Override
    public Doc getById(Long id, Long userId) {
        Doc doc = docMapper.selectById(id);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new BusinessException("文档不存在");
        }
        // 从 MongoDB 获取最新版本的内容
        List<DocContent> contents = docContentRepository.findByDocIdOrderByVersionDesc(id);
        if (!contents.isEmpty()) {
            DocContent latest = contents.get(0);
            doc.setContent(latest.getContent());
            doc.setWordCount(latest.getContent() != null ? latest.getContent().length() : 0);
        }
        // 从 folder 表获取 spaceId
        if (doc.getFolderId() != null) {
            com.kb.knowledge.entity.Folder folder = folderMapper.selectById(doc.getFolderId());
            if (folder != null) {
                doc.setSpaceId(folder.getSpaceId());
            }
        }
        return doc;
    }

    @Override
    @Transactional
    public Doc update(Long id, Long userId, DocUpdateRequest request) {
        Doc doc = getById(id, userId);

        if (request.getTitle() != null) {
            doc.setTitle(request.getTitle());
            docMapper.updateById(doc);
        }

        // 支持切换文档格式（html ↔ markdown）
        if (request.getFormat() != null && !request.getFormat().isBlank()) {
            doc.setFormat(request.getFormat());
            docMapper.updateById(doc);
        }

        if (request.getContent() != null) {
            docContentRepository.findByDocIdAndIsCurrentTrue(id).ifPresent(current -> {
                current.setIsCurrent(false);
                docContentRepository.save(current);
            });

            List<DocContent> versions = docContentRepository.findByDocIdOrderByVersionDesc(id);
            int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersion() + 1;

            DocContent content = new DocContent();
            content.setDocId(id);
            content.setUserId(userId);
            content.setContent(request.getContent());
            content.setVersion(nextVersion);
            content.setIsCurrent(true);
            content.setCreatedAt(LocalDateTime.now());
            docContentRepository.save(content);

            Version version = new Version();
            version.setResourceType("doc");
            version.setResourceId(id);
            version.setVersionNum(nextVersion);
            versionMapper.insert(version);
        }

        // 更新 MeiliSearch 索引（事务提交后执行，避免事务回滚但索引已写入的不一致）
        final String indexContent = docContentRepository.findByDocIdAndIsCurrentTrue(id)
                .map(DocContent::getContent).orElse("");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                searchIndexService.indexDoc(doc, indexContent);
            }
        });

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "MODIFY", "doc", id, "更新文档");
        return doc;
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Doc doc = getById(id, userId);
        docMapper.deleteById(id);

        // 级联清理关联数据
        // 1. MongoDB DocContent（所有版本）
        List<DocContent> contents = docContentRepository.findByDocIdOrderByVersionDesc(id);
        if (!contents.isEmpty()) {
            docContentRepository.deleteAll(contents);
        }
        // 2. MySQL Version
        versionMapper.delete(new LambdaQueryWrapper<Version>()
                .eq(Version::getResourceId, id)
                .eq(Version::getResourceType, "doc"));
        // 3. MySQL ResourceTag
        resourceTagMapper.delete(new LambdaQueryWrapper<ResourceTag>()
                .eq(ResourceTag::getResourceId, id));

        // 删除 MeiliSearch 索引（事务提交后执行，避免索引先删而数据回滚）
        final Long docId = id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                searchIndexService.removeDocIndex(docId);
            }
        });

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "doc", id,
                "删除文档: " + doc.getTitle());
    }

    @Override
    public void star(Long id, Long userId) {
        Doc doc = getById(id, userId);
        doc.setStarred(doc.getStarred() == 1 ? 0 : 1);
        docMapper.updateById(doc);
    }

    @Override
    public void move(Long id, Long userId, DocMoveRequest request) {
        Doc doc = getById(id, userId);
        doc.setFolderId(request.getFolderId());
        docMapper.updateById(doc);
    }

    @Override
    public List<DocContent> getVersions(Long id, Long userId) {
        // 校验文档存在且属于当前用户
        getById(id, userId);
        return docContentRepository.findByDocIdOrderByVersionDesc(id);
    }
}
