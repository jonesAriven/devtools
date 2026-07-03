package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.entity.Host;
import com.kb.ops.entity.OpsConflict;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.mapper.OpsConflictMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.impl.ConflictDetectionServiceImpl;
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

    @Test
    @DisplayName("检测主机异常但服务仍运行中 - 规则3")
    void detectsHostDownServiceRunning() {
        Host h1 = new Host();
        h1.setId(1L);
        h1.setName("web-1");
        h1.setIp("10.0.0.1");
        h1.setStatus(0); // 停机

        Host h2 = new Host();
        h2.setId(2L);
        h2.setName("web-2");
        h2.setIp("10.0.0.2");
        h2.setStatus(2); // 维护中

        Host h3 = new Host();
        h3.setId(3L);
        h3.setName("web-3");
        h3.setIp("10.0.0.3");
        h3.setStatus(1); // 正常

        // h1 上的运行中服务 -> 矛盾
        OpsService s1 = new OpsService();
        s1.setId(1L);
        s1.setName("svc-1");
        s1.setVersion("1.0");
        s1.setHostId(1L);
        s1.setPort(8001);
        s1.setStatus(1);

        // h2 上的运行中服务 -> 矛盾
        OpsService s2 = new OpsService();
        s2.setId(2L);
        s2.setName("svc-2");
        s2.setVersion("1.0");
        s2.setHostId(2L);
        s2.setPort(8002);
        s2.setStatus(1);

        // h3 上的运行中服务 -> 正常
        OpsService s3 = new OpsService();
        s3.setId(3L);
        s3.setName("svc-3");
        s3.setVersion("1.0");
        s3.setHostId(3L);
        s3.setPort(8003);
        s3.setStatus(1);

        // h1 上的停止服务 -> 不矛盾
        OpsService s4 = new OpsService();
        s4.setId(4L);
        s4.setName("svc-4");
        s4.setVersion("1.0");
        s4.setHostId(1L);
        s4.setPort(8004);
        s4.setStatus(0);

        when(hostMapper.selectList(any())).thenReturn(List.of(h1, h2, h3));
        when(serviceMapper.selectList(any())).thenReturn(List.of(s1, s2, s3, s4));

        int count = service.detect();

        // HOST_DOWN_SERVICE_RUNNING → 2 条
        assertEquals(2, count);
        verify(conflictMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("检测服务依赖缺失 - 规则5")
    void detectsMissingDependency() {
        OpsService s1 = new OpsService();
        s1.setId(1L);
        s1.setName("web");
        s1.setVersion("1.0");
        s1.setDependencies("redis"); // redis 存在

        OpsService s2 = new OpsService();
        s2.setId(2L);
        s2.setName("api");
        s2.setVersion("1.0");
        s2.setDependencies("missing-svc"); // 不存在

        OpsService s3 = new OpsService();
        s3.setId(3L);
        s3.setName("redis");
        s3.setVersion("1.0");

        when(serviceMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));
        when(hostMapper.selectList(any())).thenReturn(List.of());

        int count = service.detect();

        // MISSING_DEPENDENCY → 1 条
        assertEquals(1, count);
        verify(conflictMapper, times(1)).insert(any());
    }

    @Test
    @DisplayName("检测同主机同名服务 - 规则6")
    void detectsDuplicateServiceName() {
        OpsService s1 = new OpsService();
        s1.setId(1L);
        s1.setName("nginx");
        s1.setVersion("1.0");
        s1.setHostId(1L);
        s1.setPort(8001);

        OpsService s2 = new OpsService();
        s2.setId(2L);
        s2.setName("nginx"); // 同主机同名
        s2.setVersion("1.0");
        s2.setHostId(1L);
        s2.setPort(8002);

        OpsService s3 = new OpsService();
        s3.setId(3L);
        s3.setName("nginx"); // 不同主机，不重复
        s3.setVersion("1.0");
        s3.setHostId(2L);
        s3.setPort(8003);

        when(serviceMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));
        when(hostMapper.selectList(any())).thenReturn(List.of());

        int count = service.detect();

        // DUPLICATE_SERVICE_NAME → 2 条（s1 和 s2）
        assertEquals(2, count);
        verify(conflictMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("查询矛盾列表 - 分页返回")
    void list_returnsPagedResult() {
        OpsConflict c = new OpsConflict();
        c.setId(1L);
        c.setRuleCode("VERSION_MISMATCH");
        Page<OpsConflict> page = new Page<>(1, 20);
        page.setRecords(List.of(c));
        page.setTotal(1);
        when(conflictMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<OpsConflict> result = service.list("VERSION_MISMATCH", 0, 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("VERSION_MISMATCH", result.getList().get(0).getRuleCode());
    }

    @Test
    @DisplayName("解决矛盾 - 存在则标记为已解决")
    void resolve_exists_marksResolved() {
        OpsConflict c = new OpsConflict();
        c.setId(1L);
        c.setStatus(0);
        when(conflictMapper.selectById(1L)).thenReturn(c);
        when(conflictMapper.updateById(any(OpsConflict.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.resolve(1L));

        assertEquals(2, c.getStatus());
        verify(conflictMapper).updateById(any(OpsConflict.class));
    }

    @Test
    @DisplayName("解决矛盾 - 不存在抛出NotFoundException")
    void resolve_notFound_throwsNotFoundException() {
        when(conflictMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.resolve(999L));
    }
}
