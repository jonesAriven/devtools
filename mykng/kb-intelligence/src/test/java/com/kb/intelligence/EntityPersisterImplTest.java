package com.kb.intelligence;

import com.kb.intelligence.entity.*;
import com.kb.intelligence.mapper.*;
import com.kb.intelligence.mongo.ContentStorage;
import com.kb.intelligence.mongo.doc.KnContent;
import com.kb.intelligence.parser.ParseResult;
import com.kb.intelligence.service.impl.EntityPersisterImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EntityPersisterImpl 单元测试")
class EntityPersisterImplTest {

    @Mock private KnDocMapper docMapper;
    @Mock private KnHostMapper hostMapper;
    @Mock private KnServiceMapper serviceMapper;
    @Mock private KnPortMapper portMapper;
    @Mock private KnCredentialMapper credentialMapper;
    @Mock private KnDomainMapper domainMapper;
    @Mock private KnDependencyMapper dependencyMapper;
    @Mock private KnCommandMapper commandMapper;
    @Mock private KnTimelineMapper timelineMapper;
    @Mock private KnDocEntityRefMapper docEntityRefMapper;
    @Mock private ContentStorage contentStorage;

    @InjectMocks
    private EntityPersisterImpl persister;

    private KnDoc createDoc(String filePath) {
        KnDoc doc = new KnDoc();
        doc.setTitle("测试文档");
        doc.setFilePath(filePath);
        doc.setDocType("TABLE");
        return doc;
    }

    @Test
    @DisplayName("persist - 新文档含全量实体正确持久化并返回docId")
    void persist_newDocWithFullEntities_returnsDocIdAndPersistsAll() {
        ParseResult result = new ParseResult();
        KnDoc doc = createDoc("/docs/new.md");
        result.setDocMeta(doc);

        // Host A: ip 存在, 命中已有记录(字段全空) -> 触发 merge + update
        KnHost hostA = new KnHost();
        hostA.setIp("192.168.1.10");
        hostA.setName("web-01");
        hostA.setTailscaleIp("100.64.0.1");
        hostA.setUsername("admin");
        hostA.setPasswordEncrypted("pwd");
        hostA.setRole("web");
        hostA.setOsType("Ubuntu");
        hostA.setRemark("备注");
        hostA.setSshPort(2222);

        KnHost existingHostA = new KnHost();
        existingHostA.setId(200L);
        existingHostA.setIp("192.168.1.10");
        // 其余字段为空/null，确保 mergeHost 各 set 分支均触发

        // Host B: ip 存在但无已有记录 -> insert
        KnHost hostB = new KnHost();
        hostB.setIp("192.168.1.20");
        hostB.setName("db-01");

        // Host C: ip 为 null -> 跳过 selectOne -> insert
        KnHost hostC = new KnHost();
        hostC.setName("cache-01");

        result.setHosts(List.of(hostA, hostB, hostC));

        // Service A: hostId=1(待重映射), name 存在, 命中已有记录 -> merge + update
        KnService svcA = new KnService();
        svcA.setHostId(1L);
        svcA.setName("nginx");
        svcA.setVersion("1.25");
        svcA.setServiceType("web");
        svcA.setInstallPath("/usr/local/nginx");
        svcA.setRemark("svc备注");

        KnService existingSvcA = new KnService();
        existingSvcA.setId(400L);
        existingSvcA.setName("nginx");

        // Service B: hostId=2(待重映射), name 为 null -> existing=null -> insert
        KnService svcB = new KnService();
        svcB.setHostId(2L);

        // Service C: hostId 为 null, name 存在 -> existing=null -> insert
        KnService svcC = new KnService();
        svcC.setName("redis");

        result.setServices(List.of(svcA, svcB, svcC));

        // Port: hostId=1, serviceId=1 均待重映射
        KnPort port = new KnPort();
        port.setHostId(1L);
        port.setServiceId(1L);
        port.setPort(8080);
        result.setPorts(List.of(port));

        // Credential: hostId=1, serviceId=2 均待重映射
        KnCredential cred = new KnCredential();
        cred.setHostId(1L);
        cred.setServiceId(2L);
        cred.setCredType("password");
        result.setCredentials(List.of(cred));

        // Domain: targetHostId=1 待重映射
        KnDomain domain = new KnDomain();
        domain.setDomain("example.com");
        domain.setTargetHostId(1L);
        result.setDomains(List.of(domain));

        // Dependency: host->service 与 service->host 两种类型重映射
        KnDependency dep = new KnDependency();
        dep.setFromType("host");
        dep.setFromId(1L);
        dep.setToType("service");
        dep.setToId(1L);
        KnDependency dep2 = new KnDependency();
        dep2.setFromType("service");
        dep2.setFromId(2L);
        dep2.setToType("host");
        dep2.setToId(2L);
        result.setDependencies(List.of(dep, dep2));

        KnCommand cmd = new KnCommand();
        cmd.setCommand("docker run -d nginx");
        result.setCommands(List.of(cmd));

        KnTimeline tl = new KnTimeline();
        tl.setTitle("事件1");
        result.setTimelines(List.of(tl));

        KnContent content = new KnContent();
        content.setPlainText("正文");
        result.setContent(content);

        // 新文档：existing doc 为 null
        when(docMapper.selectOne(any())).thenReturn(null);
        // hostMapper.selectOne 第一次返回已有 host, 第二次返回 null（hostC 不调用）
        when(hostMapper.selectOne(any())).thenReturn(existingHostA, null);
        final AtomicLong hostIdSeq = new AtomicLong(100L);
        when(hostMapper.insert(any())).thenAnswer(inv -> {
            KnHost h = inv.getArgument(0);
            h.setId(hostIdSeq.getAndIncrement());
            return 1;
        });
        // serviceMapper.selectOne 第一次返回已有 service
        when(serviceMapper.selectOne(any())).thenReturn(existingSvcA);
        final AtomicLong svcIdSeq = new AtomicLong(500L);
        when(serviceMapper.insert(any())).thenAnswer(inv -> {
            KnService s = inv.getArgument(0);
            s.setId(svcIdSeq.getAndIncrement());
            return 1;
        });
        when(docMapper.insert(any())).thenAnswer(inv -> {
            KnDoc d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });

        Long docId = persister.persist(result);

        assertEquals(1L, docId);
        verify(docMapper).insert(any());
        verify(docMapper, atLeastOnce()).updateById(any());
        // mergeHost 触发：已有 host 字段被填充
        assertEquals("web-01", existingHostA.getName());
        assertEquals("100.64.0.1", existingHostA.getTailscaleIp());
        assertEquals("admin", existingHostA.getUsername());
        assertEquals("pwd", existingHostA.getPasswordEncrypted());
        assertEquals("web", existingHostA.getRole());
        assertEquals("Ubuntu", existingHostA.getOsType());
        assertEquals("备注", existingHostA.getRemark());
        assertEquals(2222, existingHostA.getSshPort());
        verify(hostMapper).updateById(eq(existingHostA));
        verify(hostMapper, times(2)).insert(any());
        // mergeService 触发：已有 service 字段被填充
        assertEquals("1.25", existingSvcA.getVersion());
        assertEquals("web", existingSvcA.getServiceType());
        assertEquals("/usr/local/nginx", existingSvcA.getInstallPath());
        assertEquals("svc备注", existingSvcA.getRemark());
        verify(serviceMapper).updateById(eq(existingSvcA));
        verify(serviceMapper, times(2)).insert(any());
        // 重映射验证：hostIdMap {1:200, 2:100, 3:101}, serviceIdMap {1:400, 2:500, 3:501}
        assertEquals(200L, port.getHostId());
        assertEquals(400L, port.getServiceId());
        assertEquals(200L, cred.getHostId());
        assertEquals(500L, cred.getServiceId());
        assertEquals(200L, domain.getTargetHostId());
        assertEquals(200L, dep.getFromId());
        assertEquals(400L, dep.getToId());
        assertEquals(500L, dep2.getFromId());
        assertEquals(100L, dep2.getToId());
        // docId 被设置到 command/timeline
        assertEquals(1L, cmd.getDocId());
        assertEquals(1L, tl.getDocId());
        verify(portMapper).insert(any());
        verify(credentialMapper).insert(any());
        verify(domainMapper).insert(any());
        verify(dependencyMapper, times(2)).insert(any());
        verify(commandMapper).insert(any());
        verify(timelineMapper).insert(any());
        // docEntityRef: 3 host + 3 service = 6
        verify(docEntityRefMapper, times(6)).insert(any());
        // content: 先 deleteByDocId 再 save
        verify(contentStorage).deleteByDocId(1L);
        verify(contentStorage).save(eq(content));
        assertEquals(1L, content.getDocId());
        // 实体计数 = 3 hosts + 3 services = 6
        assertEquals(6, doc.getEntityCount());
        assertEquals(1, doc.getCommandCount());
    }

    @Test
    @DisplayName("persist - 已存在文档删除旧数据并更新且content为null跳过保存")
    void persist_existingDoc_deletesOldDataAndSkipsNullContent() {
        ParseResult result = new ParseResult();
        KnDoc doc = createDoc("/docs/existing.md");
        result.setDocMeta(doc);
        result.setHosts(Collections.emptyList());
        result.setServices(Collections.emptyList());
        result.setPorts(Collections.emptyList());
        result.setCredentials(Collections.emptyList());
        result.setDomains(Collections.emptyList());
        result.setDependencies(Collections.emptyList());
        result.setCommands(Collections.emptyList());
        result.setTimelines(Collections.emptyList());
        result.setContent(null);

        KnDoc existing = new KnDoc();
        existing.setId(99L);
        existing.setFilePath("/docs/existing.md");

        when(docMapper.selectOne(any())).thenReturn(existing);

        Long docId = persister.persist(result);

        assertEquals(99L, docId);
        // deleteExistingData 触发
        verify(commandMapper).delete(any());
        verify(timelineMapper).delete(any());
        verify(docEntityRefMapper).delete(any());
        verify(contentStorage).deleteByDocId(99L);
        // 已存在文档不 insert
        verify(docMapper, never()).insert(any());
        // doc 更新（existing 分支 + 末尾统计更新）
        verify(docMapper, atLeastOnce()).updateById(any());
        // content 为 null -> persistContent 直接 return, 不调用 save
        verify(contentStorage, never()).save(any());
        // 空实体集合 -> 各子表 insert 均不触发
        verify(hostMapper, never()).insert(any());
        verify(serviceMapper, never()).insert(any());
        verify(portMapper, never()).insert(any());
        verify(docEntityRefMapper, never()).insert(any());
    }

    @Test
    @DisplayName("persist - 主机ip为null时直接insert不查询已有记录")
    void persist_hostWithNullIp_insertsDirectly() {
        ParseResult result = new ParseResult();
        KnDoc doc = createDoc("/docs/single.md");
        result.setDocMeta(doc);

        KnHost hostNoIp = new KnHost();
        hostNoIp.setName("noip-host");
        result.setHosts(List.of(hostNoIp));
        result.setServices(Collections.emptyList());
        result.setPorts(Collections.emptyList());
        result.setCredentials(Collections.emptyList());
        result.setDomains(Collections.emptyList());
        result.setDependencies(Collections.emptyList());
        result.setCommands(Collections.emptyList());
        result.setTimelines(Collections.emptyList());
        result.setContent(null);

        when(docMapper.selectOne(any())).thenReturn(null);
        when(hostMapper.insert(any())).thenAnswer(inv -> {
            KnHost h = inv.getArgument(0);
            h.setId(77L);
            return 1;
        });
        when(docMapper.insert(any())).thenAnswer(inv -> {
            KnDoc d = inv.getArgument(0);
            d.setId(2L);
            return 1;
        });

        Long docId = persister.persist(result);

        assertEquals(2L, docId);
        // ip 为 null -> 不调用 hostMapper.selectOne
        verify(hostMapper, never()).selectOne(any());
        verify(hostMapper).insert(any());
        assertEquals(77L, hostNoIp.getId());
    }

    @Test
    @DisplayName("persist - 服务无name无hostId时直接insert")
    void persist_serviceWithoutNameAndHostId_insertsDirectly() {
        ParseResult result = new ParseResult();
        KnDoc doc = createDoc("/docs/svc.md");
        result.setDocMeta(doc);

        KnService svc = new KnService();
        // name 与 hostId 均为 null
        result.setServices(List.of(svc));
        result.setHosts(Collections.emptyList());
        result.setPorts(Collections.emptyList());
        result.setCredentials(Collections.emptyList());
        result.setDomains(Collections.emptyList());
        result.setDependencies(Collections.emptyList());
        result.setCommands(Collections.emptyList());
        result.setTimelines(Collections.emptyList());
        result.setContent(null);

        when(docMapper.selectOne(any())).thenReturn(null);
        when(serviceMapper.insert(any())).thenAnswer(inv -> {
            KnService s = inv.getArgument(0);
            s.setId(88L);
            return 1;
        });
        when(docMapper.insert(any())).thenAnswer(inv -> {
            KnDoc d = inv.getArgument(0);
            d.setId(3L);
            return 1;
        });

        Long docId = persister.persist(result);

        assertEquals(3L, docId);
        // name/hostId 任一为 null -> 不调用 serviceMapper.selectOne
        verify(serviceMapper, never()).selectOne(any());
        verify(serviceMapper).insert(any());
        assertEquals(88L, svc.getId());
    }
}
