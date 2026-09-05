package com.kb.ops.service;

import com.marschat.common.page.PageResult;
import com.kb.ops.dto.OpsKnowledgeRequest;
import com.kb.ops.entity.OpsKnowledge;

public interface OpsKnowledgeService {

    PageResult<OpsKnowledge> list(String keyword, String category, Long hostId, Long serviceId, int page, int size);

    OpsKnowledge getById(Long id);

    OpsKnowledge create(OpsKnowledgeRequest request);

    OpsKnowledge update(Long id, OpsKnowledgeRequest request);

    void delete(Long id);
}
