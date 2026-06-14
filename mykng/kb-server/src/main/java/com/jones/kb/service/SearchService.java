package com.jones.kb.service;

import com.jones.kb.common.PageResult;

import java.util.List;
import java.util.Map;

public interface SearchService {

    PageResult<Map<String, Object>> search(Long userId, String q, String type, Long folderId, Long tagId, int page, int size);
}
