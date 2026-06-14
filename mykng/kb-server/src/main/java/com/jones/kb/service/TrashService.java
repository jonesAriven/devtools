package com.jones.kb.service;

import com.jones.kb.common.PageResult;

import java.util.Map;

public interface TrashService {

    PageResult<Map<String, Object>> list(Long userId, String type, int page, int size);

    void restore(Long userId, String type, Long id);

    void permanentDelete(Long userId, String type, Long id);
}
