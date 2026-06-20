package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.mapper.FolderMapper;
import com.kb.knowledge.mapper.SpaceMapper;
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
                .collect(Collectors.groupingBy(Folder::getParentId));

        List<Folder> roots = parentMap.getOrDefault(0L, new ArrayList<>());
        return roots;
    }

    @Override
    public Folder create(Long userId, FolderCreateRequest request) {
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
