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

        if (type != null && type.isBlank()) {
            type = null;
        }

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

        if (q == null || q.isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        boolean allFailed = true;
        long total = 0;
        try {
            if (type == null || "doc".equals(type)) {
                try {
                    IndexSearchResult r = searchIndex("kb_docs", q, userId, "doc", folderId);
                    results.addAll(r.hits());
                    total += r.totalHits();
                    allFailed = false;
                } catch (Exception e) {
                    log.warn("搜索 kb_docs 索引失败: {}", e.getMessage());
                }
            }
            if (type == null || "web".equals(type)) {
                try {
                    IndexSearchResult r = searchIndex("kb_webpages", q, userId, "web", folderId);
                    results.addAll(r.hits());
                    total += r.totalHits();
                    allFailed = false;
                } catch (Exception e) {
                    log.warn("搜索 kb_webpages 索引失败: {}", e.getMessage());
                }
            }
            if (type == null || "file".equals(type)) {
                try {
                    IndexSearchResult r = searchIndex("kb_files", q, userId, "file", folderId);
                    results.addAll(r.hits());
                    total += r.totalHits();
                    allFailed = false;
                } catch (Exception e) {
                    log.warn("搜索 kb_files 索引失败: {}", e.getMessage());
                }
            }

            if (allFailed || total == 0) {
                if (allFailed) {
                    log.warn("所有 MeiliSearch 索引均搜索失败，触发数据库降级搜索");
                } else {
                    log.info("MeiliSearch 搜索结果为空，尝试数据库补充搜索");
                }
                return fallbackSearch(userId, q, type, folderId, page, size);
            }

            int start = (page - 1) * size;
            int end = Math.min(start + size, results.size());
            PageResult<Map<String, Object>> pageResult;
            if (start >= results.size()) {
                pageResult = new PageResult<>(Collections.emptyList(), total, page, size);
            } else {
                pageResult = new PageResult<>(results.subList(start, end), total, page, size);
            }
            return pageResult;
        } catch (Exception e) {
            log.warn("MeiliSearch搜索失败，回退到数据库搜索: {}", e.getMessage());
            return fallbackSearch(userId, q, type, folderId, page, size);
        }
    }

    @SuppressWarnings("unchecked")
    private IndexSearchResult searchIndex(String indexUid, String q, Long userId, String resourceType, Long folderId) {
        List<Map<String, Object>> results = new ArrayList<>();
        int totalHits = 0;
        Index index = meiliSearchClient.index(indexUid);

        List<String> filters = new ArrayList<>();
        filters.add("userId = " + userId);
        if (folderId != null) {
            filters.add("folderId = " + folderId);
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .q(q)
                .filter(filters.toArray(new String[0]))
                .limit(100)
                .attributesToHighlight(new String[]{"*"})
                .highlightPreTag("<em>")
                .highlightPostTag("</em>")
                .build();

        SearchResult searchResult = (SearchResult) index.search(searchRequest);
        totalHits = searchResult.getEstimatedTotalHits();

        List<Map<String, Object>> hitsList = (List<Map<String, Object>>) (List<?>) searchResult.getHits();
        for (Map<String, Object> hitMap : hitsList) {
            Map<String, Object> item = new HashMap<>(hitMap);
            item.putIfAbsent("type", resourceType);

            if ("file".equals(resourceType) && item.get("name") != null && item.get("title") == null) {
                item.put("title", item.get("name"));
            }

            Map<String, Object> formatted = (Map<String, Object>) hitMap.get("_formatted");
            if (formatted != null) {
                // 优先使用 _formatted 的 title/name 作为高亮标题（含 <em> 标签）
                Object titleFormatted = formatted.get("title");
                if (titleFormatted == null) titleFormatted = formatted.get("name");
                if (titleFormatted != null) {
                    String titleStr = titleFormatted.toString();
                    if (titleStr.contains("<em>")) {
                        item.put("title", sanitizeHighlight(titleStr));
                    }
                }

                // 构建内容高亮片段
                StringBuilder highlight = new StringBuilder();
                Object contentFormatted = formatted.get("content");
                if (contentFormatted != null) {
                    highlight.append(extractHighlight(contentFormatted.toString(), 120));
                }
                if (highlight.isEmpty()) {
                    // 内容无高亮时，用标题高亮兜底
                    if (titleFormatted != null && titleFormatted.toString().contains("<em>")) {
                        highlight.append(sanitizeHighlight(titleFormatted.toString()));
                    }
                }
                if (highlight.length() > 0) {
                    item.put("highlight", highlight.toString());
                }
            }

            results.add(item);
        }
        return new IndexSearchResult(results, totalHits);
    }

    /**
     * 提取高亮片段。
     * <p>
     * MeiliSearch 返回的 _formatted 字段中匹配词被 &lt;em&gt;&lt;/em&gt; 包裹。
     * 本方法：
     * 1. 用占位符保护 &lt;em&gt; 标签不被 HTML 转义
     * 2. 对其余内容做 HTML 转义（防 XSS）
     * 3. 以第一个 &lt;em&gt; 为中心截取上下文，保留 &lt;em&gt; 标签
     */
    private String extractHighlight(String content, int maxLen) {
        String safe = sanitizeHighlight(content);
        if (!safe.contains("<em>")) {
            return safe.length() > maxLen ? safe.substring(0, maxLen) + "..." : safe;
        }

        // 以第一个 <em> 为中心截取上下文
        int emStart = safe.indexOf("<em>");
        int contextBefore = 30;
        int start = Math.max(0, emStart - contextBefore);
        int end = Math.min(safe.length(), start + maxLen);
        String result = safe.substring(start, end);
        if (start > 0) result = "..." + result;
        if (end < safe.length()) result = result + "...";
        return result;
    }

    /**
     * 对高亮文本做 HTML 转义，但保留 MeiliSearch 的 &lt;em&gt;&lt;/em&gt; 标签。
     */
    private String sanitizeHighlight(String content) {
        final String EM_OPEN = "\u0001";
        final String EM_CLOSE = "\u0002";
        String safe = content.replace("<em>", EM_OPEN).replace("</em>", EM_CLOSE);
        safe = safe.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
        return safe.replace(EM_OPEN, "<em>").replace(EM_CLOSE, "</em>");
    }

    private record IndexSearchResult(List<Map<String, Object>> hits, int totalHits) {}

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
                        map.put("starred", d.getStarred());
                        map.put("folderId", d.getFolderId());
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
                        map.put("starred", w.getStarred());
                        map.put("folderId", w.getFolderId());
                        results.add(map);
                    });
        }

        if (type == null || "file".equals(type)) {
            try {
                Result<List<FileDTO>> result = fileClient.searchByName(q, folderId);
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    for (FileDTO f : result.getData()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", f.getId());
                        map.put("type", "file");
                        map.put("name", f.getName());
                        map.put("title", f.getName());
                        map.put("createdAt", f.getCreatedAt());
                        map.put("starred", f.getStarred());
                        map.put("folderId", f.getFolderId());
                        results.add(map);
                    }
                }
            } catch (Exception e) {
                log.warn("Feign 调用 kb-file 搜索文件失败: {}", e.getMessage());
            }
        }

        int start = (page - 1) * size;
        int end = Math.min(start + size, results.size());
        PageResult<Map<String, Object>> pageResult;
        if (start >= results.size()) {
            pageResult = new PageResult<>(Collections.emptyList(), results.size(), page, size);
        } else {
            pageResult = new PageResult<>(results.subList(start, end), results.size(), page, size);
        }
        return pageResult;
    }

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
                        map.put("title", f.getName());
                        map.put("starred", f.getStarred());
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
                    map.put("title", d.getTitle());
                    map.put("starred", d.getStarred());
                }
            }
            case "web" -> {
                WebPage w = webPageMapper.selectById(resourceId);
                if (w != null && w.getUserId().equals(userId)) {
                    map.put("id", w.getId());
                    map.put("type", "web");
                    map.put("name", w.getTitle());
                    map.put("title", w.getTitle());
                    map.put("starred", w.getStarred());
                }
            }
        }
        return map.isEmpty() ? null : map;
    }
}
