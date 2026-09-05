package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DependencyRequest;
import com.kb.ops.entity.Dependency;
import com.kb.ops.mapper.DependencyMapper;
import com.kb.ops.service.impl.DependencyServiceImpl;
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
@DisplayName("服务依赖关系服务单元测试")
class DependencyServiceImplTest {

    @Mock
    private DependencyMapper dependencyMapper;

    @InjectMocks
    private DependencyServiceImpl dependencyService;

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        Dependency d = new Dependency();
        d.setId(1L);
        d.setServiceName("kb-auth");
        Page<Dependency> page = new Page<>(1, 20);
        page.setRecords(List.of(d));
        page.setTotal(1);
        when(dependencyMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Dependency> result = dependencyService.list(null, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("kb-auth", result.getList().get(0).getServiceName());
    }

    @Test
    @DisplayName("list_带服务ID_应用过滤条件")
    void list_withServiceId_appliesFilter() {
        Dependency d = new Dependency();
        d.setId(1L);
        Page<Dependency> page = new Page<>(1, 20);
        page.setRecords(List.of(d));
        page.setTotal(1);
        when(dependencyMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Dependency> result = dependencyService.list(1L, 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在_返回依赖")
    void getById_exists_returnsDependency() {
        Dependency d = new Dependency();
        d.setId(1L);
        d.setServiceName("kb-auth");
        when(dependencyMapper.selectById(1L)).thenReturn(d);

        Dependency result = dependencyService.getById(1L);

        assertEquals("kb-auth", result.getServiceName());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(dependencyMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> dependencyService.getById(999L));
    }

    @Test
    @DisplayName("create_类型为空_默认REQUIRED")
    void create_typeNull_defaultsToRequired() {
        DependencyRequest request = new DependencyRequest();
        request.setServiceId(1L);
        request.setServiceName("kb-auth");
        request.setDependsOnServiceId(2L);
        request.setDependsOnServiceName("mysql");
        request.setDescription("强依赖");
        when(dependencyMapper.insert(any(Dependency.class))).thenAnswer(invocation -> {
            Dependency d = invocation.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Dependency result = dependencyService.create(request);

        assertEquals("kb-auth", result.getServiceName());
        assertEquals("REQUIRED", result.getDependencyType());
        verify(dependencyMapper).insert(any(Dependency.class));
    }

    @Test
    @DisplayName("create_指定类型_保留指定类型")
    void create_withType_keepsType() {
        DependencyRequest request = new DependencyRequest();
        request.setServiceId(1L);
        request.setDependencyType("OPTIONAL");
        when(dependencyMapper.insert(any(Dependency.class))).thenAnswer(invocation -> {
            Dependency d = invocation.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Dependency result = dependencyService.create(request);

        assertEquals("OPTIONAL", result.getDependencyType());
    }

    @Test
    @DisplayName("update_存在_更新成功")
    void update_exists_updates() {
        Dependency existing = new Dependency();
        existing.setId(1L);
        existing.setServiceName("old");
        when(dependencyMapper.selectById(1L)).thenReturn(existing);
        when(dependencyMapper.updateById(any(Dependency.class))).thenReturn(1);

        DependencyRequest request = new DependencyRequest();
        request.setServiceName("new-kb-auth");
        request.setDependencyType("WEAK");

        Dependency result = dependencyService.update(1L, request);

        assertEquals("new-kb-auth", result.getServiceName());
        assertEquals("WEAK", result.getDependencyType());
        verify(dependencyMapper).updateById(any(Dependency.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(dependencyMapper.selectById(999L)).thenReturn(null);

        DependencyRequest request = new DependencyRequest();
        request.setServiceName("kb-auth");

        assertThrows(NotFoundException.class, () -> dependencyService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        Dependency existing = new Dependency();
        existing.setId(1L);
        when(dependencyMapper.selectById(1L)).thenReturn(existing);
        when(dependencyMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> dependencyService.delete(1L));

        verify(dependencyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(dependencyMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> dependencyService.delete(999L));
    }
}
