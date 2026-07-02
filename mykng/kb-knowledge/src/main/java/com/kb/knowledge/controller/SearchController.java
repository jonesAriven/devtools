package com.kb.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.page.PageResult;
import com.kb.common.result.Result;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final DocMapper docMapper;

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
        // 基于当前用户的文档标题前缀匹配，返回最多 10 条建议
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

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
