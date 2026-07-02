package com.kb.ops.service;

import com.kb.common.page.PageResult;
import com.kb.ops.entity.OperationLog;

public interface OperationLogService {
    void log(Long userId, String username, String action, String resourceType, Long resourceId, String detail, String ip);
    PageResult<OperationLog> list(Long userId, String action, String resourceType, String startTime, String endTime, int page, int size);
    OperationLog getById(Long id);
}
