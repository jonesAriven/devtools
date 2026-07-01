package com.kb.knowledge.service;

import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.dto.folder.ResourceTreeNode;
import com.kb.knowledge.entity.Folder;

import java.util.List;

public interface FolderService {

    List<Folder> getTree(Long spaceId, Long userId);

    /**
     * 获取文件夹 + 资源统一树
     *
     * @param spaceId 空间 ID
     * @param userId  当前用户 ID
     * @return 资源树节点列表（文件夹下包含 doc/file/web 资源）
     */
    List<ResourceTreeNode> getTreeWithResources(Long spaceId, Long userId);

    Folder getById(Long id, Long userId);

    Folder create(Long userId, FolderCreateRequest request);

    Folder update(Long id, Long userId, String name);

    void delete(Long id, Long userId);

    void move(Long id, Long userId, FolderMoveRequest request);

    void sort(Long id, Long userId, FolderSortRequest request);
}
