package com.kb.auth.service;

import com.kb.common.page.PageResult;
import com.kb.auth.entity.RequestLog;

public interface RequestLogService {
    void log(RequestLog requestLog);

    PageResult<RequestLog> list(String traceId, Long userId, String httpMethod, String uri,
                                String status, String serviceName, String startTime, String endTime,
                                int page, int size);

    RequestLog getById(Long id);
}
