package com.kb.ops.service;

import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DependencyRequest;
import com.kb.ops.entity.Dependency;

public interface DependencyService {

    PageResult<Dependency> list(Long serviceId, int page, int size);

    Dependency getById(Long id);

    Dependency create(DependencyRequest request);

    Dependency update(Long id, DependencyRequest request);

    void delete(Long id);
}
