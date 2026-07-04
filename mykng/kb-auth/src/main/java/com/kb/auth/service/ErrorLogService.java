package com.kb.auth.service;

import com.kb.common.page.PageResult;
import com.kb.auth.entity.ErrorLog;

public interface ErrorLogService {
    void log(Long userId, String username, String level, String source, String message, String stackTrace, String url, String ip);

    PageResult<ErrorLog> list(Long userId, String level, String source, String startTime, String endTime, int page, int size);

    ErrorLog getById(Long id);
}
