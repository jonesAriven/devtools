package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.ServiceRequest;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.impl.OpsServiceServiceImpl;
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
@DisplayName("运维服务管理服务单元测试")
class OpsServiceServiceImplTest {

    @Mock
    private OpsServiceMapper serviceMapper;

    @InjectMocks
    private OpsServiceServiceImpl opsServiceService;

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        OpsService s = new OpsService();
        s.setId(1L);
        s.setName("nginx");
        Page<OpsService> page = new Page<>(1, 20);
        page.setRecords(List.of(s));
        page.setTotal(1);
        when(serviceMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OpsService> result = opsServiceService.list(null, null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("nginx", result.getList().get(0).getName());
    }

    @Test
    @DisplayName("list_带关键字主机ID与状态_应用过滤条件")
    void list_withAllFilters_appliesFilters() {
        OpsService s = new OpsService();
        s.setId(1L);
        s.setName("nginx");
        Page<OpsService> page = new Page<>(1, 20);
        page.setRecords(List.of(s));
        page.setTotal(1);
        when(serviceMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OpsService> result = opsServiceService.list("nginx", 1L, 1, 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在_返回服务")
    void getById_exists_returnsService() {
        OpsService s = new OpsService();
        s.setId(1L);
        s.setName("nginx");
        when(serviceMapper.selectById(1L)).thenReturn(s);

        OpsService result = opsServiceService.getById(1L);

        assertEquals("nginx", result.getName());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(serviceMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> opsServiceService.getById(999L));
    }

    @Test
    @DisplayName("create_状态为空_默认状态1")
    void create_statusNull_defaultsTo1() {
        ServiceRequest request = new ServiceRequest();
        request.setName("nginx");
        request.setType("web");
        request.setVersion("1.20");
        request.setPort(80);
        request.setHostId(1L);
        request.setDeployPath("/opt/nginx");
        request.setDependencies("redis");
        request.setTags("web");
        request.setRemark("web服务");
        when(serviceMapper.insert(any(OpsService.class))).thenAnswer(invocation -> {
            OpsService s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        OpsService result = opsServiceService.create(request);

        assertEquals("nginx", result.getName());
        assertEquals(1, result.getStatus());
        assertEquals("web", result.getType());
        verify(serviceMapper).insert(any(OpsService.class));
    }

    @Test
    @DisplayName("create_指定状态_保留指定状态")
    void create_withStatus_keepsStatus() {
        ServiceRequest request = new ServiceRequest();
        request.setName("nginx");
        request.setStatus(0);
        when(serviceMapper.insert(any(OpsService.class))).thenAnswer(invocation -> {
            OpsService s = invocation.getArgument(0);
            s.setId(1L);
            return 1;
        });

        OpsService result = opsServiceService.create(request);

        assertEquals(0, result.getStatus());
    }

    @Test
    @DisplayName("update_存在_更新成功")
    void update_exists_updates() {
        OpsService existing = new OpsService();
        existing.setId(1L);
        existing.setName("old");
        when(serviceMapper.selectById(1L)).thenReturn(existing);
        when(serviceMapper.updateById(any(OpsService.class))).thenReturn(1);

        ServiceRequest request = new ServiceRequest();
        request.setName("new-nginx");
        request.setVersion("1.21");

        OpsService result = opsServiceService.update(1L, request);

        assertEquals("new-nginx", result.getName());
        assertEquals("1.21", result.getVersion());
        verify(serviceMapper).updateById(any(OpsService.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(serviceMapper.selectById(999L)).thenReturn(null);

        ServiceRequest request = new ServiceRequest();
        request.setName("nginx");

        assertThrows(NotFoundException.class, () -> opsServiceService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        OpsService existing = new OpsService();
        existing.setId(1L);
        when(serviceMapper.selectById(1L)).thenReturn(existing);
        when(serviceMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> opsServiceService.delete(1L));

        verify(serviceMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(serviceMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> opsServiceService.delete(999L));
    }

    @Test
    @DisplayName("listAll_返回全部服务")
    void listAll_returnsAll() {
        OpsService s1 = new OpsService();
        s1.setId(1L);
        OpsService s2 = new OpsService();
        s2.setId(2L);
        when(serviceMapper.selectList(null)).thenReturn(List.of(s1, s2));

        List<OpsService> result = opsServiceService.listAll();

        assertEquals(2, result.size());
    }
}
