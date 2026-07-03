package com.kb.ops;

import com.kb.ops.dto.DashboardVO;
import com.kb.ops.entity.*;
import com.kb.ops.mapper.*;
import com.kb.ops.service.impl.DashboardServiceImpl;
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
@DisplayName("运维看板服务单元测试")
class DashboardServiceImplTest {

    @Mock
    private HostMapper hostMapper;
    @Mock
    private OpsServiceMapper serviceMapper;
    @Mock
    private DeploymentRecordMapper recordMapper;
    @Mock
    private OpsConflictMapper conflictMapper;
    @Mock
    private OpsSnapshotMapper snapshotMapper;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    @DisplayName("getDashboard_有数据_返回完整看板")
    void getDashboard_withData_returnsFullDashboard() {
        Host h1 = new Host();
        h1.setStatus(1); // running
        Host h2 = new Host();
        h2.setStatus(0); // stopped
        Host h3 = new Host();
        h3.setStatus(2); // maintenance
        Host h4 = new Host();
        h4.setStatus(null); // null status
        when(hostMapper.selectList(any())).thenReturn(List.of(h1, h2, h3, h4));

        OpsService s1 = new OpsService();
        s1.setStatus(1);
        s1.setType("web");
        OpsService s2 = new OpsService();
        s2.setStatus(0);
        s2.setType("db");
        OpsService s3 = new OpsService();
        s3.setStatus(2);
        s3.setType(null); // null type -> unknown
        OpsService s4 = new OpsService();
        s4.setStatus(null);
        s4.setType("web");
        when(serviceMapper.selectList(any())).thenReturn(List.of(s1, s2, s3, s4));

        DeploymentRecord r1 = new DeploymentRecord();
        r1.setServiceName("kb-auth");
        r1.setVersion("1.0");
        r1.setOperator("admin");
        r1.setDeployTime(LocalDateTime.now());
        r1.setResult(1);
        r1.setRollback(0);
        DeploymentRecord r2 = new DeploymentRecord();
        r2.setDeployTime(null); // 空时间，覆盖过滤分支
        when(recordMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(recordMapper.selectCount(any())).thenReturn(3L);

        OpsConflict c1 = new OpsConflict();
        c1.setRuleCode("VERSION_MISMATCH");
        c1.setRuleName("版本不一致");
        c1.setSeverity(3);
        c1.setTargetName("nginx");
        c1.setDetail("详情");
        c1.setDetectedAt(LocalDateTime.now());
        OpsConflict c2 = new OpsConflict();
        c2.setDetectedAt(null); // 空时间
        when(conflictMapper.selectList(any())).thenReturn(List.of(c1, c2));
        when(conflictMapper.selectCount(any())).thenReturn(5L);

        DashboardVO vo = dashboardService.getDashboard();

        assertNotNull(vo);
        assertEquals(4L, vo.getHostStats().get("total"));
        assertEquals(1L, vo.getHostStats().get("running"));
        assertEquals(1L, vo.getHostStats().get("stopped"));
        assertEquals(1L, vo.getHostStats().get("maintenance"));
        assertEquals(4L, vo.getServiceStats().get("total"));
        assertEquals(1L, vo.getServiceStats().get("running"));
        assertEquals(1L, vo.getServiceStats().get("stopped"));
        assertEquals(1L, vo.getServiceStats().get("abnormal"));
        assertEquals(5L, vo.getUnresolvedConflictCount());
        assertEquals(3L, vo.getRecentDeployCount());
        // 服务类型分布包含 unknown
        assertTrue(vo.getServiceTypeDistribution().containsKey("unknown"));
        assertEquals(7, vo.getDeployTrend().size());
        assertFalse(vo.getRecentDeploys().isEmpty());
        assertFalse(vo.getRecentConflicts().isEmpty());
    }

    @Test
    @DisplayName("getDashboard_空数据且矛盾数为null_返回零值")
    void getDashboard_emptyData_nullUnresolved_returnsZeros() {
        when(hostMapper.selectList(any())).thenReturn(List.of());
        when(serviceMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(conflictMapper.selectList(any())).thenReturn(List.of());
        when(conflictMapper.selectCount(any())).thenReturn(null);
        when(recordMapper.selectCount(any())).thenReturn(0L);

        DashboardVO vo = dashboardService.getDashboard();

        assertNotNull(vo);
        assertEquals(0L, vo.getHostStats().get("total"));
        assertEquals(0L, vo.getServiceStats().get("total"));
        assertEquals(0L, vo.getUnresolvedConflictCount());
        assertEquals(0L, vo.getRecentDeployCount());
        assertTrue(vo.getRecentDeploys().isEmpty());
        assertTrue(vo.getRecentConflicts().isEmpty());
        assertEquals(7, vo.getDeployTrend().size());
    }

    @Test
    @DisplayName("refreshSnapshot_快照不存在_全部插入")
    void refreshSnapshot_allNew_insertsAll() {
        Host h = new Host();
        h.setStatus(1);
        OpsService s = new OpsService();
        s.setStatus(1);
        when(hostMapper.selectList(any())).thenReturn(List.of(h));
        when(serviceMapper.selectList(any())).thenReturn(List.of(s));
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(conflictMapper.selectCount(any())).thenReturn(2L);
        when(snapshotMapper.insert(any(OpsSnapshot.class))).thenReturn(1);

        assertDoesNotThrow(() -> dashboardService.refreshSnapshot());

        verify(snapshotMapper, times(5)).insert(any(OpsSnapshot.class));
        verify(snapshotMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("refreshSnapshot_快照已存在_全部更新")
    void refreshSnapshot_allExist_updatesAll() {
        Host h = new Host();
        h.setStatus(1);
        OpsService s = new OpsService();
        s.setStatus(1);
        when(hostMapper.selectList(any())).thenReturn(List.of(h));
        when(serviceMapper.selectList(any())).thenReturn(List.of(s));
        OpsSnapshot existing = new OpsSnapshot();
        when(snapshotMapper.selectOne(any())).thenReturn(existing);
        when(conflictMapper.selectCount(any())).thenReturn(null); // 覆盖null分支
        when(snapshotMapper.updateById(any(OpsSnapshot.class))).thenReturn(1);

        assertDoesNotThrow(() -> dashboardService.refreshSnapshot());

        verify(snapshotMapper, times(5)).updateById(any(OpsSnapshot.class));
        verify(snapshotMapper, never()).insert(any());
    }

    @Test
    @DisplayName("scheduledSnapshot_刷新异常_吞掉异常不抛出")
    void scheduledSnapshot_throwsException_caughtAndLogged() {
        when(hostMapper.selectList(any())).thenThrow(new RuntimeException("DB故障"));

        // 定时任务不应抛出异常
        assertDoesNotThrow(() -> dashboardService.scheduledSnapshot());

        verify(hostMapper).selectList(any());
    }
}
