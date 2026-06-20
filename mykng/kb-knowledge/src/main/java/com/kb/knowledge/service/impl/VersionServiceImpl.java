package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final VersionMapper versionMapper;
    private final DocContentRepository docContentRepository;
    private final WebContentRepository webContentRepository;

    @Override
    public List<Version> listVersions(String resourceType, Long resourceId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<Version>()
                        .eq(Version::getResourceType, resourceType)
                        .eq(Version::getResourceId, resourceId)
                        .orderByDesc(Version::getVersionNum));
    }

    @Override
    public Version getVersion(Long id) {
        Version version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        return version;
    }

    @Override
    public Version rollback(Long id, Long userId) {
        Version version = getVersion(id);

        switch (version.getResourceType()) {
            case "file" -> {
                // 文件版本内容存储在 kb-file 的 MongoDB 中，
                // 文件回滚需由 kb-file 服务处理（通过 Feign 或事件通知）
                log.info("文件版本回滚需由 kb-file 处理 fileId={} version={}",
                        version.getResourceId(), version.getVersionNum());
            }
            case "doc" -> rollbackDocContent(version.getResourceId(), version.getVersionNum());
            case "web" -> rollbackWebContent(version.getResourceId(), version.getVersionNum());
            default -> throw new BusinessException("不支持的资源类型");
        }

        return version;
    }

    private void rollbackDocContent(Long docId, int targetVersion) {
        docContentRepository.findByDocIdAndIsCurrentTrue(docId).ifPresent(current -> {
            current.setIsCurrent(false);
            docContentRepository.save(current);
        });

        docContentRepository.findByDocIdOrderByVersionDesc(docId).stream()
                .filter(dc -> dc.getVersion() == targetVersion)
                .findFirst()
                .ifPresent(target -> {
                    target.setIsCurrent(true);
                    docContentRepository.save(target);
                });
    }

    private void rollbackWebContent(Long webId, int targetVersion) {
        webContentRepository.findByWebIdAndIsCurrentTrue(webId).ifPresent(current -> {
            current.setIsCurrent(false);
            webContentRepository.save(current);
        });

        webContentRepository.findByWebIdOrderByVersionDesc(webId).stream()
                .filter(wc -> wc.getVersion() == targetVersion)
                .findFirst()
                .ifPresent(target -> {
                    target.setIsCurrent(true);
                    webContentRepository.save(target);
                });
    }
}
