package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.common.result.Result;
import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.dto.folder.ResourceTreeNode;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.FolderMapper;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.FolderService;
import com.kb.knowledge.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderMapper folderMapper;
    private final SpaceMapper spaceMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final EventPublisher eventPublisher;
    private final SearchIndexService searchIndexService;
    private final FileClient fileClient;

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

    @Override
    public List<ResourceTreeNode> getTreeWithResources(Long spaceId, Long userId) {
        // 1. 获取文件夹树（复用 getTree，内部已校验空间归属）
        List<Folder> folderTree = getTree(spaceId, userId);

        // 2. 一次性查询当前用户所有文档，按 folderId 分组（避免 N+1 查询）
        List<Doc> allDocs = docMapper.selectList(
                new LambdaQueryWrapper<Doc>().eq(Doc::getUserId, userId));
        Map<Long, List<Doc>> docsByFolder = allDocs.stream()
                .collect(Collectors.groupingBy(d -> d.getFolderId() == null ? 0L : d.getFolderId()));

        // 3. 一次性查询当前用户所有网页，按 folderId 分组
        List<WebPage> allWebPages = webPageMapper.selectList(
                new LambdaQueryWrapper<WebPage>().eq(WebPage::getUserId, userId));
        Map<Long, List<WebPage>> webPagesByFolder = allWebPages.stream()
                .collect(Collectors.groupingBy(w -> w.getFolderId() == null ? 0L : w.getFolderId()));

        // 4. 通过 Feign 一次性获取当前用户所有文件，按 folderId 分组
        Map<Long, List<FileDTO>> filesByFolder;
        try {
            Result<List<FileDTO>> fileResult = fileClient.listAll();
            List<FileDTO> allFiles = fileResult != null && fileResult.getData() != null
                    ? fileResult.getData() : Collections.emptyList();
            filesByFolder = allFiles.stream()
                    .collect(Collectors.groupingBy(f -> f.getFolderId() == null ? 0L : f.getFolderId()));
        } catch (Exception e) {
            log.warn("通过 Feign 获取文件列表失败，资源树将不包含文件: {}", e.getMessage());
            filesByFolder = Collections.emptyMap();
        }

        // 5. 将文件夹树转换为资源树，资源作为 children 填入（子文件夹之后）
        return convertToResourceTree(folderTree, docsByFolder, webPagesByFolder, filesByFolder);
    }

    /**
     * 将文件夹树转换为资源树节点
     * <p>
     * 每个文件夹节点的 children 包含：子文件夹（递归） + doc + file + web（资源在子文件夹之后）。
     */
    private List<ResourceTreeNode> convertToResourceTree(List<Folder> folders,
                                                         Map<Long, List<Doc>> docsByFolder,
                                                         Map<Long, List<WebPage>> webPagesByFolder,
                                                         Map<Long, List<FileDTO>> filesByFolder) {
        List<ResourceTreeNode> result = new ArrayList<>();
        for (Folder folder : folders) {
            ResourceTreeNode node = new ResourceTreeNode();
            node.setId(folder.getId());
            node.setName(folder.getName());
            node.setType("folder");

            List<ResourceTreeNode> children = new ArrayList<>();

            // 子文件夹（递归，放在最前）
            if (folder.getChildren() != null && !folder.getChildren().isEmpty()) {
                children.addAll(convertToResourceTree(folder.getChildren(), docsByFolder,
                        webPagesByFolder, filesByFolder));
            }

            // 文档
            for (Doc doc : docsByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                ResourceTreeNode docNode = new ResourceTreeNode();
                docNode.setId(doc.getId());
                docNode.setName(doc.getTitle());
                docNode.setType("doc");
                docNode.setFormat(doc.getFormat());
                children.add(docNode);
            }

            // 文件
            for (FileDTO file : filesByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                ResourceTreeNode fileNode = new ResourceTreeNode();
                fileNode.setId(file.getId());
                fileNode.setName(file.getName());
                fileNode.setType("file");
                children.add(fileNode);
            }

            // 网页
            for (WebPage web : webPagesByFolder.getOrDefault(folder.getId(), Collections.emptyList())) {
                ResourceTreeNode webNode = new ResourceTreeNode();
                webNode.setId(web.getId());
                webNode.setName(web.getTitle());
                webNode.setType("web");
                webNode.setUrl(web.getUrl());
                children.add(webNode);
            }

            node.setChildren(children);
            result.add(node);
        }
        return result;
    }

    @Override
    public Folder getById(Long id, Long userId) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException("文件夹不存在");
        }
        checkSpaceOwner(folder.getSpaceId(), userId);
        return folder;
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
