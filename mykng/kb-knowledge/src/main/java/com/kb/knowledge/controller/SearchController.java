package com.kb.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marschat.common.page.PageResult;
import com.marschat.common.result.Result;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.feign.FileClient;
import com.kb.knowledge.feign.dto.FileDTO;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final FileClient fileClient;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> search(
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.search(getCurrentUserId(), q, type, folderId, tagId, page, size));
    }

    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String keyword) {
        String query = (q != null && !q.isBlank()) ? q : keyword;
        if (query == null || query.isBlank()) {
            return Result.ok(new ArrayList<>());
        }
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<Doc> wrapper = new LambdaQueryWrapper<Doc>()
                .eq(Doc::getUserId, userId)
                .likeRight(Doc::getTitle, query)
                .last("LIMIT 10");
        List<Doc> docs = docMapper.selectList(wrapper);
        List<String> suggestions = docs.stream()
                .map(Doc::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
        return Result.ok(suggestions);
    }

    @GetMapping("/starred")
    public Result<PageResult<Map<String, Object>>> starred(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> allItems = new ArrayList<>();

        // 查询收藏的文档
        if (type == null || type.isBlank() || "doc".equals(type)) {
            LambdaQueryWrapper<Doc> docWrapper = new LambdaQueryWrapper<Doc>()
                    .eq(Doc::getUserId, userId)
                    .eq(Doc::getStarred, 1)
                    .orderByDesc(Doc::getUpdatedAt);
            List<Doc> docs = docMapper.selectList(docWrapper);
            for (Doc doc : docs) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", doc.getId());
                item.put("type", "doc");
                item.put("title", doc.getTitle());
                item.put("starred", doc.getStarred());
                item.put("createdAt", doc.getCreatedAt());
                item.put("updatedAt", doc.getUpdatedAt());
                allItems.add(item);
            }
        }

        // 查询收藏的网页
        if (type == null || type.isBlank() || "web".equals(type)) {
            LambdaQueryWrapper<WebPage> webWrapper = new LambdaQueryWrapper<WebPage>()
                    .eq(WebPage::getUserId, userId)
                    .eq(WebPage::getStarred, 1)
                    .orderByDesc(WebPage::getUpdatedAt);
            List<WebPage> pages = webPageMapper.selectList(webWrapper);
            for (WebPage web : pages) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", web.getId());
                item.put("type", "web");
                item.put("title", web.getTitle());
                item.put("starred", web.getStarred());
                item.put("createdAt", web.getCreatedAt());
                item.put("updatedAt", web.getUpdatedAt());
                allItems.add(item);
            }
        }

        // 查询收藏的文件（通过 Feign 调用 kb-file）
        if (type == null || type.isBlank() || "file".equals(type)) {
            try {
                var fileResult = fileClient.listAll();
                if (fileResult != null && fileResult.getData() != null) {
                    for (FileDTO file : fileResult.getData()) {
                        if (file.getStarred() != null && file.getStarred() == 1) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", file.getId());
                            item.put("type", "file");
                            item.put("name", file.getName());
                            item.put("title", file.getName());
                            item.put("starred", file.getStarred());
                            item.put("createdAt", file.getCreatedAt());
                            item.put("updatedAt", file.getUpdatedAt());
                            allItems.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取收藏文件列表失败: {}", e.getMessage());
            }
        }

        // 按更新时间倒序排序
        allItems.sort(Comparator.comparing(m -> {
            Object updatedAt = m.get("updatedAt");
            if (updatedAt == null) {
                updatedAt = m.get("createdAt");
            }
            return updatedAt == null ? "" : updatedAt.toString();
        }, Comparator.reverseOrder()));

        // 分页
        int total = allItems.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);
        List<Map<String, Object>> pageList = start < total ? allItems.subList(start, end) : new ArrayList<>();

        return Result.ok(PageResult.of(pageList, total, page, size));
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
