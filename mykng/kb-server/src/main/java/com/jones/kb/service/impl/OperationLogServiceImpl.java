package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jones.kb.common.PageResult;
import com.jones.kb.entity.OperationLog;
import com.jones.kb.mapper.OperationLogMapper;
import com.jones.kb.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Async("kbAsyncExecutor")
    @Override
    public void log(Long userId, String action, String resourceType, Long resourceId, String detail, String ip) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(userId);
        operationLog.setAction(action);
        operationLog.setResourceType(resourceType);
        operationLog.setResourceId(resourceId);
        operationLog.setDetail(detail);
        operationLog.setIp(ip);
        operationLogMapper.insert(operationLog);
    }

    @Override
    public PageResult<OperationLog> list(Long userId, int page, int size) {
        Page<OperationLog> pageParam = new Page<>(page, size);
        Page<OperationLog> result = operationLogMapper.selectPage(pageParam,
                new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getUserId, userId)
                        .orderByDesc(OperationLog::getCreatedAt));
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }
}
