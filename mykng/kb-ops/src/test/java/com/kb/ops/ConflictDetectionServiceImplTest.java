package com.kb.ops;

import com.kb.ops.entity.Host;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.mapper.OpsConflictMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.impl.ConflictDetectionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 矛盾检测服务单元测试（Mockito 模拟 Mapper，验证检测规则逻辑）。
 */
@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceImplTest {

    @Mock
    private HostMapper hostMapper;
    @Mock
    private OpsServiceMapper serviceMapper;
    @Mock
    private OpsConflictMapper conflictMapper;

    @InjectMocks
    private ConflictDetectionServiceImpl service;

    @Test
    void detectsVersionMismatchAcrossHosts() {
        // 同名服务 nginx 在两台主机上版本不一致
        OpsService s1 = new OpsService();
        s1.setId(1L);
        s1.setName("nginx");
        s1.setVersion("1.20");
        s1.setHostId(1L);

        OpsService s2 = new OpsService();
        s2.setId(2L);
        s2.setName("nginx");
        s2.setVersion("1.21");
        s2.setHostId(2L);

        when(serviceMapper.selectList(null)).thenReturn(List.of(s1, s2));
        when(hostMapper.selectList(null)).thenReturn(List.of());

        int count = service.detect();

        // VERSION_MISMATCH 规则：每个实例生成一条 → 共 2 条；其余规则不命中
        assertEquals(2, count);
        verify(conflictMapper, times(2)).insert(any());
    }

    @Test
    void detectsDuplicateHostIpAndPortConflict() {
        Host h1 = new Host();
        h1.setId(1L);
        h1.setName("web-1");
        h1.setIp("10.0.0.5");
        h1.setStatus(1);

        Host h2 = new Host();
        h2.setId(2L);
        h2.setName("web-2");
        h2.setIp("10.0.0.5"); // 重复 IP
        h2.setStatus(1);

        // 同主机同端口两个服务
        OpsService s1 = new OpsService();
        s1.setId(1L);
        s1.setName("app-a");
        s1.setVersion("1.0");
        s1.setHostId(1L);
        s1.setPort(8080);
        s1.setStatus(1);

        OpsService s2 = new OpsService();
        s2.setId(2L);
        s2.setName("app-b");
        s2.setVersion("1.0");
        s2.setHostId(1L);
        s2.setPort(8080);
        s2.setStatus(1);

        when(hostMapper.selectList(null)).thenReturn(List.of(h1, h2));
        when(serviceMapper.selectList(null)).thenReturn(List.of(s1, s2));

        int count = service.detect();

        // DUPLICATE_HOST_IP → 2 条；PORT_CONFLICT → 2 条；共 4 条
        assertTrue(count >= 4, "应检测到 IP 重复与端口冲突，实际: " + count);
        verify(conflictMapper, atLeast(4)).insert(any());
    }
}
