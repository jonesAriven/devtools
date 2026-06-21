package com.kb.ops;

import com.kb.common.page.PageResult;
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
}
