package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.FolderMapper;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.FolderService;
import com.kb.knowledge.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;
    private final SpaceMapper spaceMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final EventPublisher eventPublisher;
    private final SearchIndexService searchIndexService;

    @Override
    public List<Folder> getTree(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new BusinessException("空间不存在");
        }
        if (!space.getUserId().equals(userId)) {
            throw new BusinessException("无权限访问此空间");
        }

        List<Folder> allFolders = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getSpaceId, spaceId)
                        .orderByAsc(Folder::getSortOrder)
                        .orderByAsc(Folder::getCreatedAt));

        return buildTree(allFolders);
    }

    private List<Folder> buildTree(List<Folder> allFolders) {
        Map<Long, List<Folder>> parentMap = allFolders.stream()
                .collect(Collectors.groupingBy(f -> f.getParentId() == null ? 0L : f.getParentId()));

        List<Folder> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        setChildren(roots, parentMap);
        return roots;
    }

    private void setChildren(List<Folder> folders, Map<Long, List<Folder>> parentMap) {
        for (Folder folder : folders) {
            List<Folder> children = parentMap.getOrDefault(folder.getId(), new ArrayList<>());
            folder.setChildren(children);
            setChildren(children, parentMap);
        }
    }

    @Override
    public Folder create(Long userId, FolderCreateRequest request) {
        checkSpaceOwner(request.getSpaceId(), userId);
        Folder folder = new Folder();
        folder.setSpaceId(request.getSpaceId());
        folder.setParentId(request.getParentId() != null ? request.getParentId() : 0L);
        folder.setName(request.getName());
        folder.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        folderMapper.insert(folder);

        // 写入 MeiliSearch 索引
        searchIndexService.indexFolder(folder);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "CREATE", "folder", folder.getId(),
                "创建文件夹: " + request.getName());
        return folder;
    }

    @Override
    public Folder update(Long id, Long userId, String name) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        checkSpaceOwner(folder.getSpaceId(), userId);
        folder.setName(name);
        folderMapper.updateById(folder);

        // 更新 MeiliSearch 索引
        searchIndexService.indexFolder(folder);
        return folder;
    }

    @Override
    public void delete(Long id, Long userId) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        checkSpaceOwner(folder.getSpaceId(), userId);

        List<Folder> children = folderMapper.selectList(
                new LambdaQueryWrapper<Folder>().eq(Folder::getParentId, id));
        if (!children.isEmpty()) {
            throw new BusinessException("文件夹下存在子文件夹，无法删除");
        }

        if (docMapper.selectCount(new LambdaQueryWrapper<Doc>().eq(Doc::getFolderId, id)) > 0) {
            throw new BusinessException("目录下存在文档，请先迁移或删除");
        }
        if (webPageMapper.selectCount(new LambdaQueryWrapper<WebPage>().eq(WebPage::getFolderId, id)) > 0) {
            throw new BusinessException("目录下存在网页收藏，请先迁移或删除");
        }

        folderMapper.deleteById(id);

        // 删除 MeiliSearch 索引
        searchIndexService.removeFolderIndex(id);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "folder", id,
                "删除文件夹: " + folder.getName());
    }

    @Override
    public void move(Long id, Long userId, FolderMoveRequest request) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        checkSpaceOwner(folder.getSpaceId(), userId);

        Long targetParentId = request.getParentId();
        if (targetParentId != null && targetParentId != 0L) {
            Folder target = folderMapper.selectById(targetParentId);
            if (target == null) {
                throw new BusinessException("目标目录不存在");
            }
            Long ancestorId = targetParentId;
            while (ancestorId != null && ancestorId != 0L) {
                if (ancestorId.equals(id)) {
                    throw new BusinessException("不能将目录移动到其子目录下");
                }
                Folder ancestor = folderMapper.selectById(ancestorId);
                if (ancestor == null) {
                    break;
                }
                ancestorId = ancestor.getParentId();
            }
        }

        folder.setParentId(request.getParentId());
        folderMapper.updateById(folder);

        // 更新 MeiliSearch 索引
        searchIndexService.indexFolder(folder);
    }

    @Override
    public void sort(Long id, Long userId, FolderSortRequest request) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        checkSpaceOwner(folder.getSpaceId(), userId);
        folder.setSortOrder(request.getSortOrder());
        folderMapper.updateById(folder);

        // 更新 MeiliSearch 索引
        searchIndexService.indexFolder(folder);
    }

    private void checkSpaceOwner(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || !space.getUserId().equals(userId)) {
            throw new BusinessException("无权限操作");
        }
    }
}
