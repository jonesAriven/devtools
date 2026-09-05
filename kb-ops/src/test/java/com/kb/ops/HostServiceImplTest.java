package com.kb.ops;

import com.marschat.common.exception.BusinessException;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.HostRequest;
import com.kb.ops.entity.Host;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.service.impl.HostServiceImpl;
import com.kb.ops.util.CryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("主机管理服务单元测试")
class HostServiceImplTest {

    @Mock private HostMapper hostMapper;
    @Mock private CryptoUtil cryptoUtil;

    @InjectMocks
    private HostServiceImpl hostService;

    @Test
    @DisplayName("创建主机 - 密码加密存储")
    void createHost() {
        HostRequest request = new HostRequest();
        request.setName("web-server");
        request.setIp("192.168.1.100");
        request.setSshPort(22);
        request.setPassword("secret123");

        when(hostMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(cryptoUtil.encrypt("secret123")).thenReturn("encrypted-pwd");
        when(hostMapper.insert(any(Host.class))).thenAnswer(invocation -> {
            Host h = invocation.getArgument(0);
            h.setId(1L);
            return 1;
        });

        Host result = hostService.create(request);

        assertNotNull(result);
        assertEquals("web-server", result.getName());
        assertNull(result.getPasswordEncrypted());
        verify(cryptoUtil).encrypt("secret123");
    }

    @Test
    @DisplayName("创建主机 - 默认端口22和状态1")
    void createHostDefaults() {
        HostRequest request = new HostRequest();
        request.setName("server");
        request.setIp("10.0.0.1");

        when(hostMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(hostMapper.insert(any(Host.class))).thenAnswer(invocation -> {
            Host h = invocation.getArgument(0);
            h.setId(1L);
            return 1;
        });

        Host result = hostService.create(request);

        assertEquals(22, result.getSshPort());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("获取主机详情 - 不返回密码")
    void getByIdHidePassword() {
        Host host = new Host();
        host.setId(1L);
        host.setName("server");
        host.setPasswordEncrypted("encrypted");
        when(hostMapper.selectById(1L)).thenReturn(host);

        Host result = hostService.getById(1L, false);

        assertNotNull(result);
        assertNull(result.getPasswordEncrypted());
    }

    @Test
    @DisplayName("获取主机详情 - revealPassword=true 返回密码")
    void getByIdRevealPassword() {
        Host host = new Host();
        host.setId(1L);
        host.setName("server");
        host.setPasswordEncrypted("encrypted");
        when(hostMapper.selectById(1L)).thenReturn(host);

        Host result = hostService.getById(1L, true);

        assertEquals("encrypted", result.getPasswordEncrypted());
    }

    @Test
    @DisplayName("获取主机 - 不存在")
    void getByIdNotFound() {
        when(hostMapper.selectById(999L)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> hostService.getById(999L, false));
    }

    @Test
    @DisplayName("IP重复检查 - 抛出BusinessException")
    void createHostDuplicateIp() {
        HostRequest request = new HostRequest();
        request.setName("server2");
        request.setIp("192.168.1.100");

        Host existing = new Host();
        existing.setId(1L);
        existing.setIp("192.168.1.100");
        when(hostMapper.selectList(any())).thenReturn(List.of(existing));

        assertThrows(BusinessException.class, () -> hostService.create(request));
    }

    @Test
    @DisplayName("查询主机列表 - 关键字与状态过滤")
    void list_withKeywordAndStatus_returnsPagedResult() {
        Host h = new Host();
        h.setId(1L);
        h.setName("web");
        h.setPasswordEncrypted("secret");
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Host> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(h));
        page.setTotal(1);
        when(hostMapper.selectPage(any(), any())).thenReturn(page);

        com.marschat.common.page.PageResult<Host> result = hostService.list("web", 1, 1, 20);

        assertEquals(1, result.getTotal());
        // 列表不应返回密码
        assertNull(result.getList().get(0).getPasswordEncrypted());
    }

    @Test
    @DisplayName("查询主机列表 - 无过滤条件")
    void list_noFilter_returnsPagedResult() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Host> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(hostMapper.selectPage(any(), any())).thenReturn(page);

        com.marschat.common.page.PageResult<Host> result = hostService.list(null, null, 1, 20);

        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("更新主机 - 不存在抛出NotFoundException")
    void updateHostNotFound() {
        when(hostMapper.selectById(999L)).thenReturn(null);

        HostRequest request = new HostRequest();
        request.setName("server");
        request.setIp("10.0.0.1");

        assertThrows(NotFoundException.class, () -> hostService.update(999L, request));
    }

    @Test
    @DisplayName("更新主机 - 含密码加密且IP不重复")
    void updateHostWithPassword() {
        Host existing = new Host();
        existing.setId(1L);
        existing.setName("old");
        when(hostMapper.selectById(1L)).thenReturn(existing);
        when(hostMapper.selectList(any())).thenReturn(List.of());
        when(cryptoUtil.encrypt("newpwd")).thenReturn("encrypted");
        when(hostMapper.updateById(any(Host.class))).thenReturn(1);

        HostRequest request = new HostRequest();
        request.setName("new-name");
        request.setIp("10.0.0.2");
        request.setPassword("newpwd");

        Host result = hostService.update(1L, request);

        assertEquals("new-name", result.getName());
        // 更新后返回前应清空密码
        assertNull(result.getPasswordEncrypted());
        verify(cryptoUtil).encrypt("newpwd");
        verify(hostMapper).updateById(any(Host.class));
    }

    @Test
    @DisplayName("更新主机 - 不传密码不修改原密码")
    void updateHostWithoutPassword() {
        Host existing = new Host();
        existing.setId(1L);
        existing.setPasswordEncrypted("old-pwd");
        when(hostMapper.selectById(1L)).thenReturn(existing);
        when(hostMapper.selectList(any())).thenReturn(List.of());
        when(hostMapper.updateById(any(Host.class))).thenReturn(1);

        HostRequest request = new HostRequest();
        request.setName("new-name");
        request.setIp("10.0.0.9");

        Host result = hostService.update(1L, request);

        assertEquals("new-name", result.getName());
        verify(cryptoUtil, never()).encrypt(any());
    }

    @Test
    @DisplayName("更新主机 - IP与其他主机重复抛出BusinessException")
    void updateHostDuplicateIp() {
        Host existing = new Host();
        existing.setId(1L);
        when(hostMapper.selectById(1L)).thenReturn(existing);

        Host other = new Host();
        other.setId(2L);
        other.setIp("10.0.0.5");
        when(hostMapper.selectList(any())).thenReturn(List.of(other));

        HostRequest request = new HostRequest();
        request.setName("server");
        request.setIp("10.0.0.5");

        assertThrows(BusinessException.class, () -> hostService.update(1L, request));
    }

    @Test
    @DisplayName("删除主机 - 存在则删除成功")
    void deleteHostExists() {
        Host existing = new Host();
        existing.setId(1L);
        when(hostMapper.selectById(1L)).thenReturn(existing);
        when(hostMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> hostService.delete(1L));

        verify(hostMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除主机 - 不存在抛出NotFoundException")
    void deleteHostNotFound() {
        when(hostMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> hostService.delete(999L));
    }
}
