package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DomainRequest;
import com.kb.ops.entity.Domain;
import com.kb.ops.mapper.DomainMapper;
import com.kb.ops.service.impl.DomainServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("域名管理服务单元测试")
class DomainServiceImplTest {

    @Mock
    private DomainMapper domainMapper;

    @InjectMocks
    private DomainServiceImpl domainService;

    @Test
    @DisplayName("list_无过滤条件_返回分页结果")
    void list_noFilter_returnsPagedResult() {
        Domain d = new Domain();
        d.setId(1L);
        d.setDomain("kb.com");
        Page<Domain> page = new Page<>(1, 20);
        page.setRecords(List.of(d));
        page.setTotal(1);
        when(domainMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Domain> result = domainService.list(null, null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("kb.com", result.getList().get(0).getDomain());
    }

    @Test
    @DisplayName("list_带关键字与状态_应用过滤条件")
    void list_withKeywordAndStatus_appliesFilters() {
        Domain d = new Domain();
        d.setId(1L);
        d.setDomain("kb.com");
        Page<Domain> page = new Page<>(1, 20);
        page.setRecords(List.of(d));
        page.setTotal(1);
        when(domainMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Domain> result = domainService.list("kb", 1, 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_存在_返回域名")
    void getById_exists_returnsDomain() {
        Domain d = new Domain();
        d.setId(1L);
        d.setDomain("kb.com");
        when(domainMapper.selectById(1L)).thenReturn(d);

        Domain result = domainService.getById(1L);

        assertEquals("kb.com", result.getDomain());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(domainMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> domainService.getById(999L));
    }

    @Test
    @DisplayName("create_状态为空_默认状态1")
    void create_statusNull_defaultsTo1() {
        DomainRequest request = new DomainRequest();
        request.setDomain("new.com");
        request.setType("顶级域");
        request.setPurpose("官网");
        request.setRegistrar("阿里云");
        request.setExpiresAt(LocalDateTime.now().plusYears(1));
        when(domainMapper.insert(any(Domain.class))).thenAnswer(invocation -> {
            Domain d = invocation.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Domain result = domainService.create(request);

        assertNotNull(result);
        assertEquals("new.com", result.getDomain());
        assertEquals(1, result.getStatus());
        assertEquals("顶级域", result.getType());
        verify(domainMapper).insert(any(Domain.class));
    }

    @Test
    @DisplayName("create_指定状态_保留指定状态")
    void create_withStatus_keepsStatus() {
        DomainRequest request = new DomainRequest();
        request.setDomain("new.com");
        request.setStatus(2);
        when(domainMapper.insert(any(Domain.class))).thenAnswer(invocation -> {
            Domain d = invocation.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Domain result = domainService.create(request);

        assertEquals(2, result.getStatus());
    }

    @Test
    @DisplayName("update_存在_更新成功")
    void update_exists_updates() {
        Domain existing = new Domain();
        existing.setId(1L);
        existing.setDomain("old.com");
        when(domainMapper.selectById(1L)).thenReturn(existing);
        when(domainMapper.updateById(any(Domain.class))).thenReturn(1);

        DomainRequest request = new DomainRequest();
        request.setDomain("new.com");
        request.setPurpose("更新用途");

        Domain result = domainService.update(1L, request);

        assertEquals("new.com", result.getDomain());
        assertEquals("更新用途", result.getPurpose());
        verify(domainMapper).updateById(any(Domain.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(domainMapper.selectById(999L)).thenReturn(null);

        DomainRequest request = new DomainRequest();
        request.setDomain("new.com");

        assertThrows(NotFoundException.class, () -> domainService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        Domain existing = new Domain();
        existing.setId(1L);
        when(domainMapper.selectById(1L)).thenReturn(existing);
        when(domainMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> domainService.delete(1L));

        verify(domainMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(domainMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> domainService.delete(999L));
    }
}
