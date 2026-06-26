package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.SearchService;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final Client meiliSearchClient;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final FileClient fileClient;

    @Override
    public PageResult<Map<String, Object>> search(Long userId, String q, String type, Long folderId, Long tagId, int page, int size) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 按标签搜索
        if (tagId != null) {
            List<ResourceTag> resourceTags = resourceTagMapper.selectList(
                    new LambdaQueryWrapper<ResourceTag>()
                            .eq(ResourceTag::getTagId, tagId));
            for (ResourceTag rt : resourceTags) {
                if (type != null && !type.equals(rt.getResourceType())) continue;
                Map<String, Object> item = getResourceById(rt.getResourceType(), rt.getResourceId(), userId);
                if (item != null) results.add(item);
            }
            int start = (page - 1) * size;
            int end = Math.min(start + size, results.size());
            if (start >= results.size()) {
                return new PageResult<>(Collections.emptyList(), results.size(), page, size);
            }
            return new PageResult<>(results.subList(start, end), results.size(), page, size);
        }

        // MeiliSearch 全文搜索
        if (q == null || q.isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        try {
            long total = 0;
            if (type == null || "doc".equals(type)) {
                IndexSearchResult r = searchIndex("kb_docs", q, userId, "doc");
                results.addAll(r.hits());
                total += r.totalHits();
            }
            if (type == null || "web".equals(type)) {
                IndexSearchResult r = searchIndex("kb_webpages", q, userId, "web");
                results.addAll(r.hits());
                total += r.totalHits();
            }
            if (type == null || "file".equals(type)) {
                IndexSearchResult r = searchIndex("kb_files", q, userId, "file");
                results.addAll(r.hits());
                total += r.totalHits();
            }

            int start = (page - 1) * size;
            int end = Math.min(start + size, results.size());
            if (start >= results.size()) {
                return new PageResult<>(Collections.emptyList(), total, page, size);
            }
            return new PageResult<>(results.subList(start, end), total, page, size);
        } catch (Exception e) {
            log.warn("MeiliSearch搜索失败，回退到数据库搜索: {}", e.getMessage());
            return fallbackSearch(userId, q, type, folderId, page, size);
        }
    }

    /**
     * 搜索单个 MeiliSearch 索引
     */
    @SuppressWarnings("unchecked")
    private IndexSearchResult searchIndex(String indexUid, String q, Long userId, String resourceType) {
        List<Map<String, Object>> results = new ArrayList<>();
        int totalHits = 0;
        try {
            Index index = meiliSearchClient.index(indexUid);
            SearchRequest searchRequest = SearchRequest.builder()
                    .q(q)
                    .filter(new String[]{"userId = " + userId})
                    .limit(100)
                    .build();
            SearchResult searchResult = (SearchResult) index.search(searchRequest);
            totalHits = searchResult.getEstimatedTotalHits();
            for (Object hit : searchResult.getHits()) {
                if (hit instanceof Map) {
                    Map<String, Object> hitMap = (Map<String, Object>) hit;
                    hitMap.putIfAbsent("type", resourceType);
                    results.add(hitMap);
                }
            }
        } catch (Exception e) {
            log.warn("搜索索引 {} 失败: {}", indexUid, e.getMessage());
        }
        return new IndexSearchResult(results, totalHits);
    }

    /**
     * 单个索引的搜索结果（含命中数估算）
     */
    private record IndexSearchResult(List<Map<String, Object>> hits, int totalHits) {}

    /**
     * 数据库回退搜索（MeiliSearch 不可用时）
     */
    private PageResult<Map<String, Object>> fallbackSearch(Long userId, String q, String type, Long folderId, int page, int size) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (type == null || "doc".equals(type)) {
            docMapper.selectList(new LambdaQueryWrapper<Doc>()
                            .eq(Doc::getUserId, userId)
                            .eq(folderId != null, Doc::getFolderId, folderId)
                            .like(Doc::getTitle, q))
                    .forEach(d -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", d.getId());
                        map.put("type", "doc");
                        map.put("name", d.getTitle());
                        map.put("title", d.getTitle());
                        map.put("createdAt", d.getCreatedAt());
                        results.add(map);
                    });
        }

        if (type == null || "web".equals(type)) {
            webPageMapper.selectList(new LambdaQueryWrapper<WebPage>()
                            .eq(WebPage::getUserId, userId)
                            .eq(folderId != null, WebPage::getFolderId, folderId)
                            .like(WebPage::getTitle, q))
                    .forEach(w -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", w.getId());
                        map.put("type", "web");
                        map.put("name", w.getTitle());
                        map.put("title", w.getTitle());
                        map.put("url", w.getUrl());
                        map.put("createdAt", w.getCreatedAt());
                        results.add(map);
                    });
        }

        if (type == null || "file".equals(type)) {
            try {
                // 通过 Feign 调用 kb-file 搜索文件（回退方案）
                // 此处简化处理，实际可调用 kb-file 的搜索接口
            } catch (Exception e) {
                log.warn("Feign 调用 kb-file 搜索文件失败: {}", e.getMessage());
            }
        }

        int start = (page - 1) * size;
        int end = Math.min(start + size, results.size());
        if (start >= results.size()) {
            return new PageResult<>(Collections.emptyList(), results.size(), page, size);
        }
        return new PageResult<>(results.subList(start, end), results.size(), page, size);
    }

    /**
     * 根据资源类型和 ID 获取资源信息（标签搜索用）
     */
    private Map<String, Object> getResourceById(String resourceType, Long resourceId, Long userId) {
        Map<String, Object> map = new HashMap<>();
        switch (resourceType) {
            case "file" -> {
                try {
                    Result<FileDTO> result = fileClient.getById(resourceId);
                    if (result != null && result.getCode() == 200 && result.getData() != null
                            && userId.equals(result.getData().getUserId())) {
                        FileDTO f = result.getData();
                        map.put("id", f.getId());
                        map.put("type", "file");
                        map.put("name", f.getName());
                    }
                } catch (Exception e) {
                    log.warn("Feign 获取文件信息失败 fileId={}: {}", resourceId, e.getMessage());
                }
            }
            case "doc" -> {
                Doc d = docMapper.selectById(resourceId);
                if (d != null && d.getUserId().equals(userId)) {
                    map.put("id", d.getId());
                    map.put("type", "doc");
                    map.put("name", d.getTitle());
                }
            }
            case "web" -> {
                WebPage w = webPageMapper.selectById(resourceId);
                if (w != null && w.getUserId().equals(userId)) {
                    map.put("id", w.getId());
                    map.put("type", "web");
                    map.put("name", w.getTitle());
                }
            }
        }
        return map.isEmpty() ? null : map;
    }
}
