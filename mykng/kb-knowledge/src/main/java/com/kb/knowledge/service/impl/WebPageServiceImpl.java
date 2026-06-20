package com.kb.knowledge.service.impl;

import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.web.WebCollectRequest;
import com.kb.knowledge.entity.Version;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.mapper.VersionMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SearchIndexService;
import com.kb.knowledge.service.WebPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebPageServiceImpl implements WebPageService {

    private final WebPageMapper webPageMapper;
    private final VersionMapper versionMapper;
    private final WebContentRepository webContentRepository;
    private final EventPublisher eventPublisher;
    private final SearchIndexService searchIndexService;

    @Override
    @Transactional
    public WebPage collect(Long userId, WebCollectRequest request) {
        String htmlContent;
        try {
            htmlContent = HttpUtil.get(request.getUrl(), 10000);
        } catch (Exception e) {
            throw new BusinessException("网页采集失败: " + e.getMessage());
        }

        String title = extractTitle(htmlContent);
        if (title == null || title.isBlank()) {
            title = request.getUrl();
        }

        WebPage webPage = new WebPage();
        webPage.setFolderId(request.getFolderId());
        webPage.setUserId(userId);
        webPage.setUrl(request.getUrl());
        webPage.setTitle(title);
        webPage.setStarred(0);
        webPageMapper.insert(webPage);

        WebContent content = new WebContent();
        content.setWebId(webPage.getId());
        content.setUserId(userId);
        content.setUrl(request.getUrl());
        content.setTitle(title);
        content.setContent(htmlContent);
        content.setVersion(1);
        content.setIsCurrent(true);
        content.setCreatedAt(LocalDateTime.now());
        webContentRepository.save(content);

        Version version = new Version();
        version.setResourceType("web");
        version.setResourceId(webPage.getId());
        version.setVersionNum(1);
        versionMapper.insert(version);

        // 写入 MeiliSearch 索引
        searchIndexService.indexWebPage(webPage, htmlContent);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "COLLECT", "web", webPage.getId(),
                "采集网页: " + request.getUrl());
        return webPage;
    }

    private String extractTitle(String html) {
        if (html == null) return null;
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        if (start >= 0 && end > start) {
            return html.substring(start + 7, end).trim();
        }
        return null;
    }

    @Override
    public PageResult<WebPage> list(Long userId, Long folderId, int page, int size) {
        Page<WebPage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<WebPage> wrapper = new LambdaQueryWrapper<WebPage>()
                .eq(WebPage::getUserId, userId)
                .eq(folderId != null, WebPage::getFolderId, folderId)
                .orderByDesc(WebPage::getCreatedAt);
        Page<WebPage> result = webPageMapper.selectPage(pageParam, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public WebPage getById(Long id, Long userId) {
        WebPage webPage = webPageMapper.selectById(id);
        if (webPage == null || !webPage.getUserId().equals(userId)) {
            throw new BusinessException("网页不存在");
        }
        return webPage;
    }

    @Override
    public void delete(Long id, Long userId) {
        WebPage webPage = getById(id, userId);
        webPageMapper.deleteById(id);

        // 删除 MeiliSearch 索引
        searchIndexService.removeWebPageIndex(id);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "web", id,
                "删除网页: " + webPage.getTitle());
    }

    @Override
    public void star(Long id, Long userId) {
        WebPage webPage = getById(id, userId);
        webPage.setStarred(webPage.getStarred() == 1 ? 0 : 1);
        webPageMapper.updateById(webPage);
    }
}
