package com.kb.ops.service;

import com.kb.common.page.PageResult;
import com.kb.ops.dto.HostRequest;
import com.kb.ops.entity.Host;

public interface HostService {

    PageResult<Host> list(String keyword, Integer status, int page, int size);

    Host getById(Long id, boolean revealPassword);

    Host create(HostRequest request);

    Host update(Long id, HostRequest request);

    void delete(Long id);
}
