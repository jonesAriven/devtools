package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.PortRequest;
import com.kb.ops.entity.Port;
import com.kb.ops.mapper.PortMapper;
import com.kb.ops.service.impl.PortServiceImpl;
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
@DisplayName("端口管理服务单元测试")
class PortServiceImplTest {

    @Mock
    private PortMapper portMapper;

    @InjectMocks
    private PortServiceImpl portService;

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        Port p = new Port();
        p.setId(1L);
        p.setPort(8080);
        Page<Port> page = new Page<>(1, 20);
        page.setRecords(List.of(p));
        page.setTotal(1);
        when(portMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Port> result = portService.list(null, null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals(8080, result.getList().get(0).getPort());
    }

    @Test
    @DisplayName("list_带主机服务ID与关键字_应用过滤条件")
    void list_withAllFilters_appliesFilters() {
        Port p = new Port();
        p.setId(1L);
        Page<Port> page = new Page<>(1, 20);
        page.setRecords(List.of(p));
        page.setTotal(1);
        when(portMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Port> result = portService.list(1L, 2L, "web", 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在_返回端口")
    void getById_exists_returnsPort() {
        Port p = new Port();
        p.setId(1L);
        p.setPort(8080);
        when(portMapper.selectById(1L)).thenReturn(p);

        Port result = portService.getById(1L);

        assertEquals(8080, result.getPort());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(portMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> portService.getById(999L));
    }

    @Test
    @DisplayName("create_状态与暴露为空_默认状态1暴露0")
    void create_statusAndExposedNull_defaultsApplied() {
        PortRequest request = new PortRequest();
        request.setHostId(1L);
        request.setPort("8080");
        request.setProtocol("TCP");
        request.setServiceId(2L);
        request.setPurpose("web服务");
        request.setRemark("备注");
        when(portMapper.insert(any(Port.class))).thenAnswer(invocation -> {
            Port p = invocation.getArgument(0);
            p.setId(1L);
            return 1;
        });

        Port result = portService.create(request);

        assertEquals(8080, result.getPort());
        assertEquals("TCP", result.getProtocol());
        assertEquals(1, result.getStatus());
        assertEquals(0, result.getExposed());
        verify(portMapper).insert(any(Port.class));
    }

    @Test
    @DisplayName("create_指定状态与暴露_保留指定值")
    void create_withStatusAndExposed_keepsValues() {
        PortRequest request = new PortRequest();
        request.setPort("9090");
        request.setStatus(0);
        request.setExposed(1);
        when(portMapper.insert(any(Port.class))).thenAnswer(invocation -> {
            Port p = invocation.getArgument(0);
            p.setId(1L);
            return 1;
        });

        Port result = portService.create(request);

        assertEquals(9090, result.getPort());
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getExposed());
    }

    @Test
    @DisplayName("create_端口号格式不正确_抛出BusinessException")
    void create_invalidPort_throwsBusinessException() {
        PortRequest request = new PortRequest();
        request.setPort("abc");

        assertThrows(BusinessException.class, () -> portService.create(request));
    }

    @Test
    @DisplayName("create_端口含空格_自动trim后解析")
    void create_portWithWhitespace_trimmedAndParsed() {
        PortRequest request = new PortRequest();
        request.setPort("  8080  ");
        when(portMapper.insert(any(Port.class))).thenAnswer(invocation -> {
            Port p = invocation.getArgument(0);
            p.setId(1L);
            return 1;
        });

        Port result = portService.create(request);

        assertEquals(8080, result.getPort());
    }

    @Test
    @DisplayName("update_存在_更新成功")
    void update_exists_updates() {
        Port existing = new Port();
        existing.setId(1L);
        when(portMapper.selectById(1L)).thenReturn(existing);
        when(portMapper.updateById(any(Port.class))).thenReturn(1);

        PortRequest request = new PortRequest();
        request.setPort("3306");
        request.setProtocol("TCP");

        Port result = portService.update(1L, request);

        assertEquals(3306, result.getPort());
        assertEquals("TCP", result.getProtocol());
        verify(portMapper).updateById(any(Port.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(portMapper.selectById(999L)).thenReturn(null);

        PortRequest request = new PortRequest();
        request.setPort("3306");

        assertThrows(NotFoundException.class, () -> portService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        Port existing = new Port();
        existing.setId(1L);
        when(portMapper.selectById(1L)).thenReturn(existing);
        when(portMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> portService.delete(1L));

        verify(portMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(portMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> portService.delete(999L));
    }
}
