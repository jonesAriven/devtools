package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.page.PageResult;
import com.kb.ops.entity.OperationLog;
import com.kb.ops.mapper.OperationLogMapper;
import com.kb.ops.service.impl.OperationLogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("操作日志服务单元测试")
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper logMapper;

    @InjectMocks
    private OperationLogServiceImpl logService;

    @Test
    @DisplayName("log_正常写入_调用insert")
    void log_normal_insertsEntity() {
        when(logMapper.insert(any(OperationLog.class))).thenReturn(1);

        assertDoesNotThrow(() -> logService.log(1L, "admin", "CREATE", "HOST", 1L, "新建主机", "127.0.0.1"));

        verify(logMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("log_插入异常_吞掉异常不抛出")
    void log_insertThrows_swallowsException() {
        when(logMapper.insert(any(OperationLog.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        // 异步日志记录失败不应影响主流程
        assertDoesNotThrow(() -> logService.log(1L, "admin", "CREATE", "HOST", 1L, "新建主机", "127.0.0.1"));

        verify(logMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        OperationLog log = new OperationLog();
        log.setId(1L);
        log.setUsername("admin");
        Page<OperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(logMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OperationLog> result = logService.list(null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("admin", result.getList().get(0).getUsername());
    }

    @Test
    @DisplayName("list_带用户ID与动作_应用过滤条件")
    void list_withUserIdAndAction_appliesFilters() {
        OperationLog log = new OperationLog();
        log.setId(1L);
        Page<OperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log));
        page.setTotal(1);
        when(logMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OperationLog> result = logService.list(1L, "CREATE", 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在_返回日志")
    void getById_exists_returnsLog() {
        OperationLog log = new OperationLog();
        log.setId(1L);
        log.setUsername("admin");
        when(logMapper.selectById(1L)).thenReturn(log);

        OperationLog result = logService.getById(1L);

        assertEquals("admin", result.getUsername());
    }

    @Test
    @DisplayName("getById_不存在_返回null")
    void getById_notFound_returnsNull() {
        when(logMapper.selectById(999L)).thenReturn(null);

        OperationLog result = logService.getById(999L);

        assertNull(result);
    }
}
