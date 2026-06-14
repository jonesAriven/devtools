package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jones.kb.common.BusinessException;
import com.jones.kb.common.PageResult;
import com.jones.kb.entity.Doc;
import com.jones.kb.entity.KbFile;
import com.jones.kb.entity.WebPage;
import com.jones.kb.mapper.DocMapper;
import com.jones.kb.mapper.KbFileMapper;
import com.jones.kb.mapper.WebPageMapper;
import com.jones.kb.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {

    private final KbFileMapper kbFileMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;

    @Override
    public PageResult<Map<String, Object>> list(Long userId, String type, int page, int size) {
        List<Map<String, Object>> allItems = new ArrayList<>();

        if (type == null || "file".equals(type)) {
            kbFileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                            .eq(KbFile::getUserId, userId)
                            .eq(KbFile::getDeleted, 1))
                    .forEach(f -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", f.getId());
                        map.put("type", "file");
                        map.put("name", f.getName());
                        map.put("deletedAt", f.getUpdatedAt());
                        allItems.add(map);
                    });
        }

        if (type == null || "doc".equals(type)) {
            docMapper.selectList(new LambdaQueryWrapper<Doc>()
                            .eq(Doc::getUserId, userId)
                            .eq(Doc::getDeleted, 1))
                    .forEach(d -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", d.getId());
                        map.put("type", "doc");
                        map.put("name", d.getTitle());
                        map.put("deletedAt", d.getUpdatedAt());
                        allItems.add(map);
                    });
        }

        if (type == null || "web".equals(type)) {
            webPageMapper.selectList(new LambdaQueryWrapper<WebPage>()
                            .eq(WebPage::getUserId, userId)
                            .eq(WebPage::getDeleted, 1))
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
        switch (type) {
            case "file" -> {
                KbFile file = kbFileMapper.selectById(id);
                if (file == null || !file.getUserId().equals(userId)) {
                    throw new BusinessException("文件不存在");
                }
                file.setDeleted(0);
                kbFileMapper.updateById(file);
            }
            case "doc" -> {
                Doc doc = docMapper.selectById(id);
                if (doc == null || !doc.getUserId().equals(userId)) {
                    throw new BusinessException("文档不存在");
                }
                doc.setDeleted(0);
                docMapper.updateById(doc);
            }
            case "web" -> {
                WebPage webPage = webPageMapper.selectById(id);
                if (webPage == null || !webPage.getUserId().equals(userId)) {
                    throw new BusinessException("网页不存在");
                }
                webPage.setDeleted(0);
                webPageMapper.updateById(webPage);
            }
            default -> throw new BusinessException("不支持的资源类型");
        }
    }

    @Override
    public void permanentDelete(Long userId, String type, Long id) {
        switch (type) {
            case "file" -> kbFileMapper.deleteById(id);
            case "doc" -> docMapper.deleteById(id);
            case "web" -> webPageMapper.deleteById(id);
            default -> throw new BusinessException("不支持的资源类型");
        }
    }
}
