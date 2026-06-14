package com.jones.kb.service;

import com.jones.kb.common.PageResult;
import com.jones.kb.entity.OperationLog;

public interface OperationLogService {

    void log(Long userId, String action, String resourceType, Long resourceId, String detail, String ip);

    PageResult<OperationLog> list(Long userId, int page, int size);
}
