package com.kb.ops.service;

import com.kb.common.page.PageResult;
import com.kb.ops.dto.PortRequest;
import com.kb.ops.entity.Port;

public interface PortService {

    PageResult<Port> list(Long hostId, Long serviceId, String keyword, int page, int size);

    Port getById(Long id);

    Port create(PortRequest request);

    Port update(Long id, PortRequest request);

    void delete(Long id);
}
