package com.kb.knowledge.service;

import com.kb.common.page.PageResult;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.Share;

public interface ShareService {

    Share create(Long userId, ShareCreateRequest request);

    PageResult<Share> list(Long userId, int page, int size);

    void delete(Long id, Long userId);

    Share verify(String code, String extractCode);

    Object getDetail(String code, String extractCode);
}
