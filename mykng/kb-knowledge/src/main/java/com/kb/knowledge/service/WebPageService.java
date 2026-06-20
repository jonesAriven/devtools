package com.kb.knowledge.service;

import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.web.WebCollectRequest;
import com.kb.knowledge.entity.WebPage;

public interface WebPageService {

    WebPage collect(Long userId, WebCollectRequest request);

    PageResult<WebPage> list(Long userId, Long folderId, int page, int size);

    WebPage getById(Long id, Long userId);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);
}
