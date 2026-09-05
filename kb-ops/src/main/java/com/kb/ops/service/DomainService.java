package com.kb.ops.service;

import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DomainRequest;
import com.kb.ops.entity.Domain;

public interface DomainService {

    PageResult<Domain> list(String keyword, Integer status, int page, int size);

    Domain getById(Long id);

    Domain create(DomainRequest request);

    Domain update(Long id, DomainRequest request);

    void delete(Long id);
}
