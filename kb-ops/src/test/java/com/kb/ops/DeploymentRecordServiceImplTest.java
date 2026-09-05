package com.kb.ops;

import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DeploymentRecordRequest;
import com.kb.ops.entity.DeploymentRecord;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.DeploymentRecordMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.impl.DeploymentRecordServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("部署记录服务单元测试")
class DeploymentRecordServiceImplTest {

    @Mock private DeploymentRecordMapper recordMapper;
    @Mock private OpsServiceMapper serviceMapper;

    @InjectMocks
    private DeploymentRecordServiceImpl deployService;

    @Test
    @DisplayName("查询部署记录列表")
    void listDeployments() {
        DeploymentRecord r1 = new DeploymentRecord();
        r1.setId(1L);
        r1.setHostId(1L);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeploymentRecord> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(r1));
        page.setTotal(1);
        when(recordMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<DeploymentRecord> result = deployService.list(null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("创建部署记录")
    void createDeployment() {
        OpsService svc = new OpsService();
        svc.setId(1L);
        svc.setName("kb-auth");
        svc.setHostId(1L);
        when(serviceMapper.selectById(1L)).thenReturn(svc);

        DeploymentRecordRequest request = new DeploymentRecordRequest();
        request.setHostId(1L);
        request.setServiceId(1L);
        request.setVersion("1.0.0");

        when(recordMapper.insert(any(DeploymentRecord.class))).thenAnswer(invocation -> {
            DeploymentRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        });

        assertDoesNotThrow(() -> deployService.create(request));
        verify(recordMapper).insert(any(DeploymentRecord.class));
        verify(serviceMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询部署记录 - 带服务ID过滤")
    void listDeployments_withServiceId() {
        DeploymentRecord r1 = new DeploymentRecord();
        r1.setId(1L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeploymentRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(r1));
        page.setTotal(1);
        when(recordMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<DeploymentRecord> result = deployService.list(1L, 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("创建部署记录 - 主机ID为空时使用服务主机ID")
    void createDeployment_hostIdNull_usesServiceHostId() {
        OpsService svc = new OpsService();
        svc.setId(1L);
        svc.setName("kb-auth");
        svc.setHostId(5L);
        when(serviceMapper.selectById(1L)).thenReturn(svc);
        when(recordMapper.insert(any(DeploymentRecord.class))).thenAnswer(invocation -> {
            DeploymentRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        });

        DeploymentRecordRequest request = new DeploymentRecordRequest();
        request.setServiceId(1L);
        request.setVersion("1.0.0");
        // hostId 不设置 -> 使用 svc.getHostId()
        // result 不设置 -> 默认 1，version 非空 -> 触发版本更新

        DeploymentRecord result = deployService.create(request);

        assertEquals(5L, result.getHostId());
        assertEquals(1, result.getResult());
        assertEquals(0, result.getRollback());
        // 部署成功且版本非空 -> 同步更新服务版本
        verify(serviceMapper).updateById(any(OpsService.class));
    }

    @Test
    @DisplayName("创建部署记录 - 部署失败不更新服务版本")
    void createDeployment_resultFailed_noVersionUpdate() {
        OpsService svc = new OpsService();
        svc.setId(1L);
        svc.setName("kb-auth");
        svc.setHostId(1L);
        when(serviceMapper.selectById(1L)).thenReturn(svc);
        when(recordMapper.insert(any(DeploymentRecord.class))).thenAnswer(invocation -> {
            DeploymentRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        });

        DeploymentRecordRequest request = new DeploymentRecordRequest();
        request.setServiceId(1L);
        request.setVersion("1.0.0");
        request.setResult(0); // 失败

        DeploymentRecord result = deployService.create(request);

        assertEquals(0, result.getResult());
        verify(serviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("创建部署记录 - 版本为空不更新服务版本")
    void createDeployment_versionNull_noVersionUpdate() {
        OpsService svc = new OpsService();
        svc.setId(1L);
        svc.setName("kb-auth");
        svc.setHostId(1L);
        when(serviceMapper.selectById(1L)).thenReturn(svc);
        when(recordMapper.insert(any(DeploymentRecord.class))).thenAnswer(invocation -> {
            DeploymentRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        });

        DeploymentRecordRequest request = new DeploymentRecordRequest();
        request.setServiceId(1L);
        request.setResult(1);
        request.setRollback(1); // 显式设置回滚标记
        request.setRollbackInfo("回滚原因");

        DeploymentRecord result = deployService.create(request);

        assertEquals(1, result.getRollback());
        assertEquals("回滚原因", result.getRollbackInfo());
        verify(serviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("创建部署记录 - 服务不存在抛出NotFoundException")
    void createDeployment_serviceNotFound() {
        when(serviceMapper.selectById(999L)).thenReturn(null);

        DeploymentRecordRequest request = new DeploymentRecordRequest();
        request.setServiceId(999L);

        assertThrows(NotFoundException.class, () -> deployService.create(request));
    }

    @Test
    @DisplayName("查询最近部署记录 - 限制条数")
    void recent_returnsLimitedRecords() {
        DeploymentRecord r1 = new DeploymentRecord();
        r1.setId(1L);
        DeploymentRecord r2 = new DeploymentRecord();
        r2.setId(2L);
        when(recordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        List<DeploymentRecord> result = deployService.recent(5);

        assertEquals(2, result.size());
    }
}
