package com.kb.knowledge.service.impl;

import com.marschat.common.exception.BusinessException;
import com.marschat.common.exception.NoPermissionException;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.TrashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {

    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final FileClient fileClient;

    @Override
    public PageResult<Map<String, Object>> list(Long userId, String type, int page, int size) {
        List<Map<String, Object>> allItems = new ArrayList<>();
        String lowerType = type != null ? type.toLowerCase() : null;

        // 文件回收站：通过 Feign 调用 kb-file
        if (lowerType == null || "file".equals(lowerType)) {
            try {
                Result<List<FileDTO>> fileResult = fileClient.listTrash(userId);
                if (fileResult != null && fileResult.getCode() == 200 && fileResult.getData() != null) {
                    for (FileDTO f : fileResult.getData()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", f.getId());
                        map.put("type", "file");
                        map.put("name", f.getName());
                        map.put("deletedAt", f.getUpdatedAt());
                        allItems.add(map);
                    }
                }
            } catch (Exception e) {
                log.warn("Feign 获取文件回收站列表失败: {}", e.getMessage());
            }
        }

        // 文档回收站：本地查询（绕过 @TableLogic）
        if (lowerType == null || "doc".equals(lowerType)) {
            docMapper.selectTrashList(userId)
                    .forEach(d -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", d.getId());
                        map.put("type", "doc");
                        map.put("name", d.getTitle());
                        map.put("deletedAt", d.getUpdatedAt());
                        allItems.add(map);
                    });
        }

        // 网页回收站：本地查询（绕过 @TableLogic）
        if (lowerType == null || "web".equals(lowerType)) {
            webPageMapper.selectTrashList(userId)
                    .forEach(w -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", w.getId());
                        map.put("type", "web");
                        map.put("name", w.getTitle());
                        map.put("deletedAt", w.getUpdatedAt());
                        allItems.add(map);
                    });
        }

        int start = (page - 1) * size;
        int end = Math.min(start + size, allItems.size());
        if (start >= allItems.size()) {
            return new PageResult<>(Collections.emptyList(), allItems.size(), page, size);
        }
        return new PageResult<>(allItems.subList(start, end), allItems.size(), page, size);
    }

    @Override
    public void restore(Long userId, String type, Long id) {
        String lowerType = type != null ? type.toLowerCase() : null;
        switch (lowerType) {
            case "file" -> {
                // 通过 Feign 调用 kb-file 恢复文件
                try {
                    fileClient.restore(id);
                } catch (Exception e) {
                    throw new BusinessException("恢复文件失败: " + e.getMessage());
                }
            }
            case "doc" -> {
                Doc doc = docMapper.selectDeletedById(id);
                if (doc == null || !doc.getUserId().equals(userId)) {
                    throw new BusinessException("文档不存在");
                }
                docMapper.restoreById(id);
            }
            case "web" -> {
                WebPage webPage = webPageMapper.selectDeletedById(id);
                if (webPage == null || !webPage.getUserId().equals(userId)) {
                    throw new BusinessException("网页不存在");
                }
                webPageMapper.restoreById(id);
            }
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    @Override
    public void permanentDelete(Long userId, String type, Long id) {
        String lowerType = type != null ? type.toLowerCase() : null;
        switch (lowerType) {
            case "file" -> {
                // 通过 Feign 调用 kb-file 永久删除文件
                try {
                    fileClient.permanentDelete(id);
                } catch (Exception e) {
                    throw new BusinessException("永久删除文件失败: " + e.getMessage());
                }
            }
            case "doc" -> {
                Doc doc = docMapper.selectDeletedById(id);
                if (doc == null) {
                    throw new BusinessException("文档不存在");
                }
                if (!doc.getUserId().equals(userId)) {
                    throw new NoPermissionException();
                }
                docMapper.physicalDeleteById(id);
            }
            case "web" -> {
                WebPage webPage = webPageMapper.selectDeletedById(id);
                if (webPage == null) {
                    throw new BusinessException("网页不存在");
                }
                if (!webPage.getUserId().equals(userId)) {
                    throw new NoPermissionException();
                }
                webPageMapper.physicalDeleteById(id);
            }
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    @Override
    public void empty(Long userId) {
        try {
            fileClient.emptyTrash(userId);
        } catch (Exception e) {
            log.warn("Feign 清空文件回收站失败: {}", e.getMessage());
        }
        docMapper.physicalDeleteAllByUserId(userId);
        webPageMapper.physicalDeleteAllByUserId(userId);
    }
}
