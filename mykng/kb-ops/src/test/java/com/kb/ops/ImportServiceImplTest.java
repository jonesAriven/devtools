package com.kb.ops;

import com.kb.common.exception.BusinessException;
import com.kb.ops.dto.ImportRequest;
import com.kb.ops.dto.ImportResult;
import com.kb.ops.entity.Host;
import com.kb.ops.entity.OpsKnowledge;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.mapper.OpsKnowledgeMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.impl.ImportServiceImpl;
import com.kb.ops.util.CryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("运维知识导入服务单元测试")
class ImportServiceImplTest {

    @Mock
    private HostMapper hostMapper;

    @Mock
    private OpsServiceMapper serviceMapper;

    @Mock
    private OpsKnowledgeMapper knowledgeMapper;

    @Mock
    private CryptoUtil cryptoUtil;

    @InjectMocks
    private ImportServiceImpl importService;

    private Map<String, String> row(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("importData_请求为空_抛出BusinessException")
    void importData_nullRequest_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> importService.importData(null));
    }

    @Test
    @DisplayName("importData_数据行为空_抛出BusinessException")
    void importData_emptyRows_throwsBusinessException() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setRows(List.of());

        assertThrows(BusinessException.class, () -> importService.importData(req));
    }

    @Test
    @DisplayName("importData_不支持的类型_抛出BusinessException")
    void importData_unsupportedType_throwsBusinessException() {
        ImportRequest req = new ImportRequest();
        req.setType("UNKNOWN");
        req.setRows(List.of(row("name", "x")));

        assertThrows(BusinessException.class, () -> importService.importData(req));
    }

    @Test
    @DisplayName("importData_类型大小写不敏感_HOST小写可识别")
    void importData_typeLowercase_recognized() {
        ImportRequest req = new ImportRequest();
        req.setType("host");
        req.setRows(List.of(row("name", "web", "ip", "10.0.0.1")));
        when(hostMapper.selectOne(any())).thenReturn(null);
        when(hostMapper.insert(any(Host.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
    }

    @Test
    @DisplayName("importHosts_新主机_插入成功")
    void importHosts_newHost_insertsSuccess() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setRows(List.of(row(
                "name", "web-1", "ip", "10.0.0.1", "sshPort", "22",
                "username", "root", "password", "pwd123",
                "role", "web", "status", "1", "tags", "web")));
        when(hostMapper.selectOne(any())).thenReturn(null);
        when(cryptoUtil.encrypt("pwd123")).thenReturn("encrypted");
        when(hostMapper.insert(any(Host.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertEquals(0, result.getSkipped());
        verify(hostMapper).insert(any(Host.class));
        verify(cryptoUtil).encrypt("pwd123");
    }

    @Test
    @DisplayName("importHosts_已存在且不覆盖_跳过")
    void importHosts_existingNotOverride_skipped() {
        Host exist = new Host();
        exist.setId(1L);
        exist.setIp("10.0.0.1");
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setOverride(false);
        req.setRows(List.of(row("name", "web-1", "ip", "10.0.0.1")));
        when(hostMapper.selectOne(any())).thenReturn(exist);

        ImportResult result = importService.importData(req);

        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getSkipped());
        verify(hostMapper, never()).insert(any());
        verify(hostMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("importHosts_已存在且覆盖_更新成功")
    void importHosts_existingWithOverride_updatesSuccess() {
        Host exist = new Host();
        exist.setId(1L);
        exist.setIp("10.0.0.1");
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setOverride(true);
        req.setRows(List.of(row("name", "web-new", "ip", "10.0.0.1", "status", "2")));
        when(hostMapper.selectOne(any())).thenReturn(exist);
        when(hostMapper.updateById(any(Host.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        verify(hostMapper).updateById(any(Host.class));
        verify(hostMapper, never()).insert(any());
    }

    @Test
    @DisplayName("importHosts_名称或IP为空_记入失败")
    void importHosts_emptyNameOrIp_recordedAsFailed() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setRows(List.of(row("name", "web-1")));

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("第1行"));
    }

    @Test
    @DisplayName("importHosts_字段名忽略大小写_可识别")
    void importHosts_caseInsensitiveKey_recognized() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        // 使用大写 Name 与 IP 字段名
        req.setRows(List.of(row("Name", "web-1", "IP", "10.0.0.1")));
        when(hostMapper.selectOne(any())).thenReturn(null);
        when(hostMapper.insert(any(Host.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
    }

    @Test
    @DisplayName("importHosts_端口号非法_使用默认值")
    void importHosts_invalidSshPort_usesDefault() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setRows(List.of(row("name", "web-1", "ip", "10.0.0.1", "sshPort", "abc")));
        when(hostMapper.selectOne(any())).thenReturn(null);
        when(hostMapper.insert(any(Host.class))).thenAnswer(invocation -> {
            Host h = invocation.getArgument(0);
            // 默认端口22
            assertEquals(22, h.getSshPort());
            return 1;
        });

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
    }

    @Test
    @DisplayName("importServices_新服务_插入成功")
    void importServices_newService_insertsSuccess() {
        ImportRequest req = new ImportRequest();
        req.setType("SERVICE");
        req.setRows(List.of(row(
                "name", "nginx", "type", "web", "version", "1.20",
                "port", "80", "hostId", "1", "deployPath", "/opt/nginx",
                "status", "1", "dependencies", "redis", "tags", "web")));
        when(serviceMapper.selectOne(any())).thenReturn(null);
        when(serviceMapper.insert(any(OpsService.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        verify(serviceMapper).insert(any(OpsService.class));
    }

    @Test
    @DisplayName("importServices_已存在且不覆盖_跳过")
    void importServices_existingNotOverride_skipped() {
        OpsService exist = new OpsService();
        exist.setId(1L);
        exist.setName("nginx");
        ImportRequest req = new ImportRequest();
        req.setType("SERVICE");
        req.setOverride(false);
        req.setRows(List.of(row("name", "nginx")));
        when(serviceMapper.selectOne(any())).thenReturn(exist);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSkipped());
        verify(serviceMapper, never()).insert(any());
        verify(serviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("importServices_已存在且覆盖_更新成功")
    void importServices_existingWithOverride_updatesSuccess() {
        OpsService exist = new OpsService();
        exist.setId(1L);
        exist.setName("nginx");
        ImportRequest req = new ImportRequest();
        req.setType("SERVICE");
        req.setOverride(true);
        req.setRows(List.of(row("name", "nginx", "version", "1.21")));
        when(serviceMapper.selectOne(any())).thenReturn(exist);
        when(serviceMapper.updateById(any(OpsService.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        verify(serviceMapper).updateById(any(OpsService.class));
    }

    @Test
    @DisplayName("importServices_名称为空_记入失败")
    void importServices_emptyName_recordedAsFailed() {
        ImportRequest req = new ImportRequest();
        req.setType("SERVICE");
        req.setRows(List.of(row("type", "web")));

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    @DisplayName("importServices_非法hostId_置为null")
    void importServices_invalidHostId_setToNull() {
        ImportRequest req = new ImportRequest();
        req.setType("SERVICE");
        req.setRows(List.of(row("name", "nginx", "hostId", "not-a-number")));
        when(serviceMapper.selectOne(any())).thenReturn(null);
        when(serviceMapper.insert(any(OpsService.class))).thenAnswer(invocation -> {
            OpsService s = invocation.getArgument(0);
            assertNull(s.getHostId());
            return 1;
        });

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
    }

    @Test
    @DisplayName("importKnowledge_新知识_插入成功且阅读量为0")
    void importKnowledge_new_insertsSuccess() {
        ImportRequest req = new ImportRequest();
        req.setType("KNOWLEDGE");
        req.setRows(List.of(row(
                "title", "部署手册", "category", "部署", "content", "内容",
                "tags", "tag1", "hostId", "1", "serviceId", "2", "author", "admin")));
        when(knowledgeMapper.selectOne(any())).thenReturn(null);
        when(knowledgeMapper.insert(any(OpsKnowledge.class))).thenAnswer(invocation -> {
            OpsKnowledge k = invocation.getArgument(0);
            assertEquals(0, k.getViewCount());
            return 1;
        });

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        verify(knowledgeMapper).insert(any(OpsKnowledge.class));
    }

    @Test
    @DisplayName("importKnowledge_已存在且不覆盖_跳过")
    void importKnowledge_existingNotOverride_skipped() {
        OpsKnowledge exist = new OpsKnowledge();
        exist.setId(1L);
        exist.setTitle("部署手册");
        ImportRequest req = new ImportRequest();
        req.setType("KNOWLEDGE");
        req.setOverride(false);
        req.setRows(List.of(row("title", "部署手册")));
        when(knowledgeMapper.selectOne(any())).thenReturn(exist);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSkipped());
    }

    @Test
    @DisplayName("importKnowledge_已存在且覆盖_更新成功")
    void importKnowledge_existingWithOverride_updatesSuccess() {
        OpsKnowledge exist = new OpsKnowledge();
        exist.setId(1L);
        exist.setTitle("部署手册");
        ImportRequest req = new ImportRequest();
        req.setType("KNOWLEDGE");
        req.setOverride(true);
        req.setRows(List.of(row("title", "部署手册", "content", "新内容")));
        when(knowledgeMapper.selectOne(any())).thenReturn(exist);
        when(knowledgeMapper.updateById(any(OpsKnowledge.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getSuccess());
        verify(knowledgeMapper).updateById(any(OpsKnowledge.class));
        verify(knowledgeMapper, never()).insert(any());
    }

    @Test
    @DisplayName("importKnowledge_标题为空_记入失败")
    void importKnowledge_emptyTitle_recordedAsFailed() {
        ImportRequest req = new ImportRequest();
        req.setType("KNOWLEDGE");
        req.setRows(List.of(row("category", "部署")));

        ImportResult result = importService.importData(req);

        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    @DisplayName("importData_多行混合_正确统计")
    void importData_mixedRows_correctStats() {
        ImportRequest req = new ImportRequest();
        req.setType("HOST");
        req.setOverride(false);
        req.setRows(List.of(
                row("name", "ok-1", "ip", "10.0.0.1"),
                row("name", "ok-2", "ip", "10.0.0.2"),
                row("name", "missing-ip")));
        when(hostMapper.selectOne(any())).thenReturn(null);
        when(hostMapper.insert(any(Host.class))).thenReturn(1);

        ImportResult result = importService.importData(req);

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
    }
}
