package com.jones.kb.service;

import com.jones.kb.common.PageResult;
import com.jones.kb.dto.web.WebCollectRequest;
import com.jones.kb.entity.WebPage;

public interface WebPageService {

    WebPage collect(Long userId, WebCollectRequest request);

    PageResult<WebPage> list(Long userId, Long folderId, int page, int size);

    WebPage getById(Long id, Long userId);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);
}
