package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.dto.folder.FolderCreateRequest;
import com.jones.kb.dto.folder.FolderMoveRequest;
import com.jones.kb.dto.folder.FolderSortRequest;
import com.jones.kb.entity.Folder;
import com.jones.kb.entity.Space;
import com.jones.kb.mapper.FolderMapper;
import com.jones.kb.mapper.SpaceMapper;
import com.jones.kb.service.FolderService;
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
    }

    private void checkSpaceOwner(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || !space.getUserId().equals(userId)) {
            throw new BusinessException("无权限操作");
        }
    }
}
