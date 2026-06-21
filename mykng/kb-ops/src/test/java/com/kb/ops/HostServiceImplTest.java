package com.kb.ops;

import com.kb.common.exception.BusinessException;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
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
}
