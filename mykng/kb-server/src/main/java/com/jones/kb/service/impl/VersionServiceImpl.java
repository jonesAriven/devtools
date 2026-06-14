package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.entity.Version;
import com.jones.kb.mapper.VersionMapper;
import com.jones.kb.mongo.doc.DocContent;
import com.jones.kb.mongo.doc.FileContent;
import com.jones.kb.mongo.doc.WebContent;
import com.jones.kb.mongo.repository.DocContentRepository;
import com.jones.kb.mongo.repository.FileContentRepository;
import com.jones.kb.mongo.repository.WebContentRepository;
import com.jones.kb.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final VersionMapper versionMapper;
    private final FileContentRepository fileContentRepository;
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
            case "file" -> rollbackFileContent(version.getResourceId(), version.getVersionNum());
            case "doc" -> rollbackDocContent(version.getResourceId(), version.getVersionNum());
            case "web" -> rollbackWebContent(version.getResourceId(), version.getVersionNum());
            default -> throw new BusinessException("不支持的资源类型");
        }

        return version;
    }

    private void rollbackFileContent(Long fileId, int targetVersion) {
        fileContentRepository.findByFileIdAndIsCurrentTrue(fileId).ifPresent(current -> {
            current.setIsCurrent(false);
            fileContentRepository.save(current);
        });

        fileContentRepository.findByFileIdOrderByVersionDesc(fileId).stream()
                .filter(fc -> fc.getVersion() == targetVersion)
                .findFirst()
                .ifPresent(target -> {
                    target.setIsCurrent(true);
                    fileContentRepository.save(target);
                });
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
