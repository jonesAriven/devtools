package com.jones.kb.service;

import com.jones.kb.common.PageResult;
import com.jones.kb.dto.share.ShareCreateRequest;
import com.jones.kb.entity.Share;

public interface ShareService {

    Share create(Long userId, ShareCreateRequest request);

    PageResult<Share> list(Long userId, int page, int size);

    void delete(Long id, Long userId);

    Share verify(String code, String extractCode);

    Object getDetail(String code, String extractCode);
}
