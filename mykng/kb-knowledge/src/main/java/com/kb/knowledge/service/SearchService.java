package com.kb.knowledge.service;

import com.marschat.common.page.PageResult;

import java.util.List;
import java.util.Map;

public interface SearchService {

    PageResult<Map<String, Object>> search(Long userId, String q, String type, Long folderId, Long tagId, int page, int size);
}
