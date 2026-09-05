package com.kb.knowledge.service;

import com.marschat.common.page.PageResult;
import com.kb.knowledge.dto.share.ShareCreateRequest;
import com.kb.knowledge.entity.Share;

import java.util.List;

public interface ShareService {

    Share create(Long userId, ShareCreateRequest request);

    PageResult<Share> list(Long userId, int page, int size);

    List<Share> listMyShares(Long userId);

    void delete(Long id, Long userId);

    Share verify(String code, String extractCode);

    Object getDetail(String code, String extractCode);
}
