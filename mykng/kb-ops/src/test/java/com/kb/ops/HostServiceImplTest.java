package com.kb.ops;

import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.HostRequest;
import com.kb.ops.entity.Host;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.service.impl.HostServiceImpl;
import com.kb.ops.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        when(hostMapper.selectOne(any())).thenReturn(null);
        when(cryptoUtil.encrypt("secret123")).thenReturn("encrypted-pwd");
        when(hostMapper.insert(any(Host.class))).thenAnswer(invocation -> {
            Host h = invocation.getArgument(0);
            h.setId(1L);
            return 1;
        });

        Host result = hostService.create(request);

        assertNotNull(result);
        assertEquals("web-server", result.getName());
        assertNull(result.getPasswordEncrypted()); // 返回时清除密码
        verify(cryptoUtil).encrypt("secret123");
    }

    @Test
    @DisplayName("创建主机 - 默认端口22和状态1")
    void createHostDefaults() {
        HostRequest request = new HostRequest();
        request.setName("server");
        request.setIp("10.0.0.1");

        when(hostMapper.selectOne(any())).thenReturn(null);
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
    @DisplayName("列表查询 - 清除密码字段")
    void listClearsPasswords() {
        Host h1 = new Host();
        h1.setId(1L);
        h1.setName("server1");
        h1.setPasswordEncrypted("enc1");
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Host> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(java.util.List.of(h1));
        page.setTotal(1);
        when(hostMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Host> result = hostService.list(null, null, 1, 20);

        assertNotNull(result);
        assertNull(result.getList().get(0).getPasswordEncrypted());
    }
}
