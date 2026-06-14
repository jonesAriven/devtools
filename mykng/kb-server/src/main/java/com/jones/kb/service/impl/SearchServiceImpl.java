package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.common.PageResult;
import com.jones.kb.entity.*;
import com.jones.kb.mapper.*;
import com.jones.kb.mongo.doc.DocContent;
import com.jones.kb.mongo.doc.FileContent;
import com.jones.kb.mongo.doc.WebContent;
import com.jones.kb.mongo.repository.DocContentRepository;
import com.jones.kb.mongo.repository.FileContentRepository;
import com.jones.kb.mongo.repository.WebContentRepository;
import com.jones.kb.service.SearchService;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final Client meiliSearchClient;
    private final KbFileMapper kbFileMapper;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final FileContentRepository fileContentRepository;
    private final DocContentRepository docContentRepository;
    private final WebContentRepository webContentRepository;

    @Override
    public PageResult<Map<String, Object>> search(Long userId, String q, String type, Long folderId, Long tagId, int page, int size) {
        List<Map<String, Object>> results = new ArrayList<>();

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

        try {
            String indexUid = "kb_resources";
            Index index = meiliSearchClient.index(indexUid);

            SearchRequest searchRequest = SearchRequest.builder()
                    .q(q)
                    .filter(new String[]{"userId = " + userId})
                    .limit(size)
                    .offset((page - 1) * size)
                    .build();

            Searchable searchable = index.search(searchRequest);
            long total = searchable.getHits() != null ? searchable.getHits().size() : 0;

            for (Object hit : searchable.getHits()) {
                if (hit instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> hitMap = (Map<String, Object>) hit;
                    results.add(hitMap);
                }
            }
            return new PageResult<>(results, total, page, size);
        } catch (Exception e) {
            log.warn("MeiliSearch搜索失败，回退到数据库搜索: {}", e.getMessage());
            return fallbackSearch(userId, q, type, folderId, page, size);
        }
    }

    private PageResult<Map<String, Object>> fallbackSearch(Long userId, String q, String type, Long folderId, int page, int size) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (type == null || "file".equals(type)) {
            kbFileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                            .eq(KbFile::getUserId, userId)
                            .eq(folderId != null, KbFile::getFolderId, folderId)
                            .like(KbFile::getName, q))
                    .forEach(f -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", f.getId());
                        map.put("type", "file");
                        map.put("name", f.getName());
                        map.put("createdAt", f.getCreatedAt());
                        results.add(map);
                    });
        }

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
                        map.put("createdAt", w.getCreatedAt());
                        results.add(map);
                    });
        }

        int start = (page - 1) * size;
        int end = Math.min(start + size, results.size());
        if (start >= results.size()) {
            return new PageResult<>(Collections.emptyList(), results.size(), page, size);
        }
        return new PageResult<>(results.subList(start, end), results.size(), page, size);
    }

    private Map<String, Object> getResourceById(String resourceType, Long resourceId, Long userId) {
        Map<String, Object> map = new HashMap<>();
        switch (resourceType) {
            case "file" -> {
                KbFile f = kbFileMapper.selectById(resourceId);
                if (f != null && f.getUserId().equals(userId)) {
                    map.put("id", f.getId());
                    map.put("type", "file");
                    map.put("name", f.getName());
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
