package com.kb.intelligence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.intelligence.dto.response.DocContentVO;
import com.kb.intelligence.dto.response.DocEntitiesVO;
import com.kb.intelligence.dto.response.DocIndexVO;
import com.kb.intelligence.dto.response.SearchResultVO;
import com.kb.intelligence.entity.*;
import com.kb.intelligence.mapper.*;
import com.kb.intelligence.mongo.ContentStorage;
import com.kb.intelligence.mongo.doc.KnContent;
import com.kb.intelligence.service.KnowledgeQueryService;
import com.kb.intelligence.service.impl.KnowledgeQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeQueryService 单元测试")
class KnowledgeQueryServiceTest {

    @Mock private KnDocMapper docMapper;
    @Mock private KnHostMapper hostMapper;
    @Mock private KnServiceMapper serviceMapper;
    @Mock private KnPortMapper portMapper;
    @Mock private KnCredentialMapper credentialMapper;
    @Mock private KnDomainMapper domainMapper;
    @Mock private KnCommandMapper commandMapper;
    @Mock private KnTimelineMapper timelineMapper;
    @Mock private KnDocEntityRefMapper docEntityRefMapper;
    @Mock private ContentStorage contentStorage;

    @InjectMocks
    private KnowledgeQueryServiceImpl queryService;

    private KnDoc createDoc(Long id, String title, String docType) {
        KnDoc doc = new KnDoc();
        doc.setId(id);
        doc.setTitle(title);
        doc.setDocType(docType);
        doc.setCategory("运维");
        doc.setTags("#Docker,#Nginx");
        doc.setSummary("测试摘要");
        doc.setEntityCount(3);
        doc.setCommandCount(2);
        doc.setSectionCount(5);
        doc.setWordCount(1000);
        doc.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        doc.setUpdatedAt(LocalDateTime.of(2024, 6, 1, 12, 0));
        return doc;
    }

    private KnHost createHost(Long id, String ip, String name) {
        KnHost host = new KnHost();
        host.setId(id);
        host.setIp(ip);
        host.setName(name);
        host.setSshPort(22);
        host.setUsername("admin");
        host.setRole("web");
        host.setOsType("Ubuntu");
        host.setStatus("running");
        return host;
    }

    @Test
    @DisplayName("listDocs - 正常返回文档列表")
    void listDocs_normal_returnsDocList() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        Page<KnDoc> docPage = new Page<>(1, 10);
        docPage.setRecords(List.of(doc));
        docPage.setTotal(1);
        when(docMapper.selectPage(any(Page.class), any())).thenReturn(docPage);

        Page<DocIndexVO> result = queryService.listDocs("TABLE", null, null, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("测试文档", result.getRecords().get(0).getTitle());
        assertEquals("TABLE", result.getRecords().get(0).getDocType());
    }

    @Test
    @DisplayName("listDocs - 无数据返回空列表")
    void listDocs_emptyData_returnsEmptyList() {
        Page<KnDoc> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0);
        when(docMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

        Page<DocIndexVO> result = queryService.listDocs(null, null, null, 1, 10);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("getDocMeta - 正常返回文档元数据")
    void getDocMeta_normal_returnsDocMeta() {
        KnDoc doc = createDoc(1L, "测试文档", "PLAN");
        when(docMapper.selectById(1L)).thenReturn(doc);

        DocIndexVO vo = queryService.getDocMeta(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals("测试文档", vo.getTitle());
        assertEquals("PLAN", vo.getDocType());
    }

    @Test
    @DisplayName("getDocMeta - 文档不存在返回null")
    void getDocMeta_notFound_returnsNull() {
        when(docMapper.selectById(999L)).thenReturn(null);

        DocIndexVO vo = queryService.getDocMeta(999L);

        assertNull(vo);
    }

    @Test
    @DisplayName("getDocEntities - 无关联实体返回空列表")
    void getDocEntities_noRefs_emptyResult() {
        KnDoc doc = createDoc(1L, "测试文档", "GENERAL");
        when(docEntityRefMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getDocId());
        assertEquals("测试文档", vo.getTitle());
        assertEquals(0, vo.getTotalEntities());
    }

    @Test
    @DisplayName("getDocEntities - 文档不存在仍返回VO")
    void getDocEntities_docNotFound_returnsEmptyVO() {
        when(docEntityRefMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(docMapper.selectById(999L)).thenReturn(null);
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(999L);

        assertNotNull(vo);
        assertEquals(999L, vo.getDocId());
        assertNull(vo.getTitle());
    }

    @Test
    @DisplayName("getDocEntities - 关联主机实体正确返回")
    void getDocEntities_withHostRef_returnsHost() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef ref = new KnDocEntityRef();
        ref.setDocId(1L);
        ref.setEntityType("host");
        ref.setEntityId(10L);

        KnHost host = createHost(10L, "192.168.1.10", "web-01");

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(ref));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(hostMapper.selectBatchIds(any())).thenReturn(List.of(host));
        when(portMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(credentialMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo);
        assertNotNull(vo.getHosts());
        assertEquals(1, vo.getHosts().size());
        assertEquals("192.168.1.10", vo.getHosts().get(0).getIp());
        assertEquals("web-01", vo.getHosts().get(0).getName());
        assertEquals(1, vo.getTotalEntities());
    }

    @Test
    @DisplayName("search - 关键词搜索返回结果")
    void search_withQuery_returnsResults() {
        KnDoc doc = createDoc(1L, "Nginx部署指南", "PLAN");
        when(docMapper.selectList(any())).thenReturn(List.of(doc));

        List<SearchResultVO> results = queryService.search("Nginx", null, null, 1, 10);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getDocId());
        assertEquals("Nginx部署指南", results.get(0).getDocTitle());
        assertEquals("PLAN", results.get(0).getDocType());
    }

    @Test
    @DisplayName("search - 无匹配结果返回空列表")
    void search_noMatch_returnsEmptyList() {
        when(docMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<SearchResultVO> results = queryService.search("不存在的关键词", null, null, 1, 10);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search - 按docType过滤搜索")
    void search_byDocType_filteredResults() {
        KnDoc doc = createDoc(1L, "文档", "TABLE");
        when(docMapper.selectList(any())).thenReturn(List.of(doc));

        List<SearchResultVO> results = queryService.search(null, List.of("TABLE"), null, 1, 10);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("getStats - 正常返回统计数据")
    void getStats_normal_returnsStatsMap() {
        when(docMapper.selectCount(any())).thenReturn(5L);
        when(hostMapper.selectCount(any())).thenReturn(3L);
        when(serviceMapper.selectCount(any())).thenReturn(10L);
        when(portMapper.selectCount(any())).thenReturn(20L);
        when(credentialMapper.selectCount(any())).thenReturn(8L);
        when(commandMapper.selectCount(any())).thenReturn(15L);
        when(domainMapper.selectCount(any())).thenReturn(2L);
        when(timelineMapper.selectCount(any())).thenReturn(7L);

        Map<String, Object> stats = queryService.getStats();

        assertNotNull(stats);
        assertEquals(5L, stats.get("docCount"));
        assertEquals(3L, stats.get("hostCount"));
        assertEquals(10L, stats.get("serviceCount"));
        assertEquals(20L, stats.get("portCount"));
        assertEquals(8L, stats.get("credentialCount"));
        assertEquals(15L, stats.get("commandCount"));
        assertEquals(2L, stats.get("domainCount"));
        assertEquals(7L, stats.get("timelineCount"));
    }

    @Test
    @DisplayName("getStats - 无数据返回零计数")
    void getStats_emptyData_returnsZeroCounts() {
        when(docMapper.selectCount(any())).thenReturn(0L);
        when(hostMapper.selectCount(any())).thenReturn(0L);
        when(serviceMapper.selectCount(any())).thenReturn(0L);
        when(portMapper.selectCount(any())).thenReturn(0L);
        when(credentialMapper.selectCount(any())).thenReturn(0L);
        when(commandMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectCount(any())).thenReturn(0L);
        when(timelineMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> stats = queryService.getStats();

        assertEquals(0L, stats.get("docCount"));
        assertEquals(0L, stats.get("hostCount"));
        assertEquals(0L, stats.get("serviceCount"));
        assertEquals(0L, stats.get("portCount"));
        assertNotNull(stats.get("byType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) stats.get("byType");
        assertTrue(byType.isEmpty());
    }

    @Test
    @DisplayName("getStats - byType包含有数据的类型")
    void getStats_withData_byTypePopulated() {
        when(docMapper.selectCount(any())).thenReturn(5L);
        when(hostMapper.selectCount(any())).thenReturn(0L);
        when(serviceMapper.selectCount(any())).thenReturn(0L);
        when(portMapper.selectCount(any())).thenReturn(0L);
        when(credentialMapper.selectCount(any())).thenReturn(0L);
        when(commandMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectCount(any())).thenReturn(0L);
        when(timelineMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> stats = queryService.getStats();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) stats.get("byType");
        assertFalse(byType.isEmpty());
        boolean hasTable = byType.stream().anyMatch(m -> "TABLE".equals(m.get("docType")));
        assertTrue(hasTable);
    }

    @Test
    @DisplayName("listHosts - 按IP过滤返回主机列表")
    void listHosts_byIp_returnsHosts() {
        KnHost host = createHost(1L, "192.168.1.10", "web-01");
        when(hostMapper.selectList(any())).thenReturn(List.of(host));

        List<DocEntitiesVO.HostVO> hosts = queryService.listHosts("192.168", null, null);

        assertEquals(1, hosts.size());
        assertEquals("192.168.1.10", hosts.get(0).getIp());
    }

    @Test
    @DisplayName("listHosts - 无匹配返回空列表")
    void listHosts_noMatch_returnsEmptyList() {
        when(hostMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<DocEntitiesVO.HostVO> hosts = queryService.listHosts(null, null, null);

        assertTrue(hosts.isEmpty());
    }

    @Test
    @DisplayName("listCommands - 按docId过滤返回命令列表")
    void listCommands_byDocId_returnsCommands() {
        KnCommand cmd = new KnCommand();
        cmd.setId(1L);
        cmd.setDocId(10L);
        cmd.setCommand("docker run -d nginx");
        cmd.setCategory("container");
        cmd.setRiskLevel("low");
        cmd.setOsType("linux");
        when(commandMapper.selectList(any())).thenReturn(List.of(cmd));

        List<DocEntitiesVO.CommandVO> commands = queryService.listCommands(10L, null, null);

        assertEquals(1, commands.size());
        assertEquals("docker run -d nginx", commands.get(0).getCommand());
        assertEquals("container", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("listTimelines - 按docId过滤返回时间线")
    void listTimelines_byDocId_returnsTimelines() {
        KnTimeline tl = new KnTimeline();
        tl.setId(1L);
        tl.setDocId(10L);
        tl.setEventTime("2024-06-01");
        tl.setTitle("系统故障");
        tl.setSeverity("high");
        tl.setEventType("incident");
        when(timelineMapper.selectList(any())).thenReturn(List.of(tl));

        List<DocEntitiesVO.TimelineVO> timelines = queryService.listTimelines(10L, null, null);

        assertEquals(1, timelines.size());
        assertEquals("系统故障", timelines.get(0).getTitle());
        assertEquals("high", timelines.get(0).getSeverity());
    }

    @Test
    @DisplayName("getDocContent - 文档不存在返回null")
    void getDocContent_docNotFound_returnsNull() {
        when(docMapper.selectById(999L)).thenReturn(null);

        DocContentVO vo = queryService.getDocContent(999L);

        assertNull(vo);
    }

    @Test
    @DisplayName("getDocContent - 无内容存储返回空VO")
    void getDocContent_noContentStorage_returnsEmptyVO() {
        KnDoc doc = createDoc(1L, "测试文档", "GENERAL");
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(contentStorage.findByDocId(1L)).thenReturn(Optional.empty());

        var vo = queryService.getDocContent(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getDocId());
        assertEquals("测试文档", vo.getTitle());
        assertNull(vo.getPlainText());
    }

    @Test
    @DisplayName("listServices - 按hostId过滤返回服务列表")
    void listServices_byHostId_returnsServices() {
        KnService svc = new KnService();
        svc.setId(1L);
        svc.setHostId(10L);
        svc.setName("nginx");
        svc.setServiceType("web");
        svc.setVersion("1.25");
        svc.setStatus("running");
        when(serviceMapper.selectList(any())).thenReturn(List.of(svc));

        List<DocEntitiesVO.ServiceVO> services = queryService.listServices(10L, "nginx");

        assertEquals(1, services.size());
        assertEquals("nginx", services.get(0).getName());
        assertEquals("web", services.get(0).getServiceType());
        assertEquals("1.25", services.get(0).getVersion());
        assertEquals(10L, services.get(0).getHostId());
    }

    @Test
    @DisplayName("listServices - 无匹配返回空列表")
    void listServices_noMatch_returnsEmptyList() {
        when(serviceMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<DocEntitiesVO.ServiceVO> services = queryService.listServices(null, null);

        assertTrue(services.isEmpty());
    }

    @Test
    @DisplayName("getDocEntities - 关联主机与服务返回全部实体VO")
    void getDocEntities_withHostAndServiceRefs_returnsAllEntities() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef hostRef = new KnDocEntityRef();
        hostRef.setDocId(1L);
        hostRef.setEntityType("host");
        hostRef.setEntityId(10L);
        KnDocEntityRef svcRef = new KnDocEntityRef();
        svcRef.setDocId(1L);
        svcRef.setEntityType("service");
        svcRef.setEntityId(20L);

        KnHost host = createHost(10L, "192.168.1.10", "web-01");
        host.setTailscaleIp("100.64.0.1");

        KnService svc = new KnService();
        svc.setId(20L);
        svc.setHostId(10L);
        svc.setName("nginx");
        svc.setServiceType("web");
        svc.setVersion("1.25");
        svc.setStatus("running");

        KnPort port = new KnPort();
        port.setId(30L);
        port.setHostId(10L);
        port.setServiceId(20L);
        port.setPort(8080);
        port.setProtocol("tcp");
        port.setAccessUrl("http://192.168.1.10:8080");
        port.setExposed(1);

        KnCredential cred = new KnCredential();
        cred.setId(40L);
        cred.setHostId(10L);
        cred.setCredType("password");
        cred.setUsername("admin");
        cred.setPasswordEncrypted("secretpwd");

        KnDomain domain = new KnDomain();
        domain.setId(50L);
        domain.setDomain("example.com");
        domain.setSubDomain("www");
        domain.setTargetHostId(10L);
        domain.setTargetPort(443);
        domain.setStatus("active");

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(hostRef, svcRef));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(hostMapper.selectBatchIds(any())).thenReturn(List.of(host));
        when(portMapper.selectList(any())).thenReturn(List.of(port));
        when(credentialMapper.selectList(any())).thenReturn(List.of(cred));
        when(serviceMapper.selectBatchIds(any())).thenReturn(List.of(svc));
        when(domainMapper.selectList(any())).thenReturn(List.of(domain));
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo);
        assertEquals(1, vo.getHosts().size());
        assertEquals(1, vo.getServices().size());
        assertEquals(1, vo.getPorts().size());
        assertEquals(1, vo.getCredentials().size());
        assertEquals(1, vo.getDomains().size());
        assertEquals("192.168.1.10", vo.getHosts().get(0).getIp());
        assertEquals("100.64.0.1", vo.getHosts().get(0).getTailscaleIp());
        assertEquals(8080, vo.getPorts().get(0).getPort());
        assertEquals("tcp", vo.getPorts().get(0).getProtocol());
        assertEquals("http://192.168.1.10:8080", vo.getPorts().get(0).getAccessUrl());
        assertEquals(1, vo.getPorts().get(0).getExposed());
        assertEquals("admin", vo.getCredentials().get(0).getUsername());
        assertEquals("s***d", vo.getCredentials().get(0).getPasswordHint());
        assertEquals("example.com", vo.getDomains().get(0).getDomain());
        assertEquals(443, vo.getDomains().get(0).getTargetPort());
        assertEquals(4, vo.getTotalEntities());
    }

    @Test
    @DisplayName("getDocEntities - 凭证密码为空或过短时密码提示为null")
    void getDocEntities_shortPassword_passwordHintNull() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef hostRef = new KnDocEntityRef();
        hostRef.setDocId(1L);
        hostRef.setEntityType("host");
        hostRef.setEntityId(10L);

        KnHost host = createHost(10L, "192.168.1.10", "web-01");
        KnCredential cred = new KnCredential();
        cred.setId(40L);
        cred.setHostId(10L);
        cred.setCredType("password");
        cred.setUsername("admin");
        cred.setPasswordEncrypted("ab");

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(hostRef));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(hostMapper.selectBatchIds(any())).thenReturn(List.of(host));
        when(portMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(credentialMapper.selectList(any())).thenReturn(List.of(cred));
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo.getCredentials());
        assertEquals(1, vo.getCredentials().size());
        assertNull(vo.getCredentials().get(0).getPasswordHint());
    }

    @Test
    @DisplayName("getDocEntities - 凭证密码为null时密码提示为null")
    void getDocEntities_nullPassword_passwordHintNull() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef hostRef = new KnDocEntityRef();
        hostRef.setDocId(1L);
        hostRef.setEntityType("host");
        hostRef.setEntityId(10L);

        KnHost host = createHost(10L, "192.168.1.10", "web-01");
        KnCredential cred = new KnCredential();
        cred.setId(40L);
        cred.setHostId(10L);
        cred.setCredType("token");
        cred.setUsername("admin");
        cred.setPasswordEncrypted(null);

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(hostRef));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(hostMapper.selectBatchIds(any())).thenReturn(List.of(host));
        when(portMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(credentialMapper.selectList(any())).thenReturn(List.of(cred));
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNull(vo.getCredentials().get(0).getPasswordHint());
    }

    @Test
    @DisplayName("getDocEntities - 主机存在但域名首次查询为空时触发回退查询")
    void getDocEntities_hostExistsButDomainEmpty_fallbackQuery() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef hostRef = new KnDocEntityRef();
        hostRef.setDocId(1L);
        hostRef.setEntityType("host");
        hostRef.setEntityId(10L);

        KnHost host = createHost(10L, "192.168.1.10", "web-01");

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(hostRef));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(hostMapper.selectBatchIds(any())).thenReturn(List.of(host));
        when(portMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(credentialMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList(), Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo);
        assertNotNull(vo.getDomains());
        assertTrue(vo.getDomains().isEmpty());
        verify(domainMapper, times(2)).selectList(any());
    }

    @Test
    @DisplayName("getDocEntities - 仅关联服务无主机时正确返回服务")
    void getDocEntities_onlyServiceRef_returnsServices() {
        KnDoc doc = createDoc(1L, "测试文档", "TABLE");
        KnDocEntityRef svcRef = new KnDocEntityRef();
        svcRef.setDocId(1L);
        svcRef.setEntityType("service");
        svcRef.setEntityId(20L);

        KnService svc = new KnService();
        svc.setId(20L);
        svc.setHostId(10L);
        svc.setName("nginx");
        svc.setServiceType("web");
        svc.setStatus("running");

        when(docEntityRefMapper.selectList(any())).thenReturn(List.of(svcRef));
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(serviceMapper.selectBatchIds(any())).thenReturn(List.of(svc));
        when(domainMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(commandMapper.selectList(any())).thenReturn(Collections.emptyList());

        DocEntitiesVO vo = queryService.getDocEntities(1L);

        assertNotNull(vo);
        assertEquals(1, vo.getServices().size());
        assertEquals("nginx", vo.getServices().get(0).getName());
        assertEquals(1, vo.getTotalEntities());
    }

    @Test
    @DisplayName("search - 按标签过滤返回结果")
    void search_byTags_returnsResults() {
        KnDoc doc = createDoc(1L, "Nginx", "PLAN");
        when(docMapper.selectList(any())).thenReturn(List.of(doc));

        List<SearchResultVO> results = queryService.search(null, null, List.of("Docker"), 1, 10);

        assertEquals(1, results.size());
        assertEquals("Nginx", results.get(0).getDocTitle());
        assertEquals("PLAN", results.get(0).getDocType());
        assertEquals(1.0f, results.get(0).getScore());
    }

    @Test
    @DisplayName("search - 关键词与docType和标签同时过滤")
    void search_queryAndDocTypeAndTags_combinedFilter() {
        KnDoc doc = createDoc(1L, "Docker部署", "PLAN");
        when(docMapper.selectList(any())).thenReturn(List.of(doc));

        List<SearchResultVO> results = queryService.search("Docker", List.of("plan"), List.of("运维"), 1, 5);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getDocId());
    }

    @Test
    @DisplayName("getDocContent - 有内容存储返回完整VO含章节")
    void getDocContent_withContent_returnsFullVO() {
        KnDoc doc = createDoc(1L, "测试文档", "GENERAL");
        KnContent content = new KnContent();
        content.setDocId(1L);
        content.setPlainText("正文内容");
        content.setWordCount(100);
        KnContent.Section section = new KnContent.Section();
        section.setTitle("章节1");
        section.setLevel(1);
        section.setContent("章节内容");
        section.setWordCount(50);
        content.setSections(List.of(section));

        when(docMapper.selectById(1L)).thenReturn(doc);
        when(contentStorage.findByDocId(1L)).thenReturn(Optional.of(content));

        DocContentVO vo = queryService.getDocContent(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getDocId());
        assertEquals("测试文档", vo.getTitle());
        assertEquals("正文内容", vo.getPlainText());
        assertEquals(100, vo.getWordCount());
        assertEquals(1, vo.getSections().size());
        assertEquals("章节1", vo.getSections().get(0).getTitle());
        assertEquals(1, vo.getSections().get(0).getLevel());
        assertEquals("章节内容", vo.getSections().get(0).getContent());
        assertEquals(50, vo.getSections().get(0).getWordCount());
    }

    @Test
    @DisplayName("listHosts - 按名称和角色过滤返回主机")
    void listHosts_byNameAndRole_returnsHosts() {
        KnHost host = createHost(1L, "192.168.1.10", "web-01");
        host.setRole("web");
        when(hostMapper.selectList(any())).thenReturn(List.of(host));

        List<DocEntitiesVO.HostVO> hosts = queryService.listHosts(null, "web-01", "web");

        assertEquals(1, hosts.size());
        assertEquals("web-01", hosts.get(0).getName());
        assertEquals("web", hosts.get(0).getRole());
    }

    @Test
    @DisplayName("listCommands - 按分类和风险等级过滤")
    void listCommands_byCategoryAndRisk_returnsCommands() {
        KnCommand cmd = new KnCommand();
        cmd.setId(1L);
        cmd.setDocId(10L);
        cmd.setCommand("rm -rf /");
        cmd.setCategory("danger");
        cmd.setRiskLevel("high");
        cmd.setOsType("linux");
        when(commandMapper.selectList(any())).thenReturn(List.of(cmd));

        List<DocEntitiesVO.CommandVO> commands = queryService.listCommands(10L, "danger", "high");

        assertEquals(1, commands.size());
        assertEquals("rm -rf /", commands.get(0).getCommand());
        assertEquals("danger", commands.get(0).getCategory());
        assertEquals("high", commands.get(0).getRiskLevel());
        assertEquals("linux", commands.get(0).getOsType());
    }

    @Test
    @DisplayName("listTimelines - 按严重级别和事件类型过滤")
    void listTimelines_bySeverityAndType_returnsTimelines() {
        KnTimeline tl = new KnTimeline();
        tl.setId(1L);
        tl.setDocId(10L);
        tl.setEventTime("2024-06-01");
        tl.setTitle("系统故障");
        tl.setDescription("描述");
        tl.setSeverity("high");
        tl.setEventType("incident");
        tl.setStatus("resolved");
        tl.setSolution("重启服务");
        when(timelineMapper.selectList(any())).thenReturn(List.of(tl));

        List<DocEntitiesVO.TimelineVO> timelines = queryService.listTimelines(10L, "high", "incident");

        assertEquals(1, timelines.size());
        assertEquals("系统故障", timelines.get(0).getTitle());
        assertEquals("描述", timelines.get(0).getDescription());
        assertEquals("resolved", timelines.get(0).getStatus());
        assertEquals("重启服务", timelines.get(0).getSolution());
    }
}
