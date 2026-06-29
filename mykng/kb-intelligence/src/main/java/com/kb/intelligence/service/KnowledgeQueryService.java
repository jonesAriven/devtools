package com.kb.intelligence.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.intelligence.dto.response.DocContentVO;
import com.kb.intelligence.dto.response.DocEntitiesVO;
import com.kb.intelligence.dto.response.DocIndexVO;
import com.kb.intelligence.dto.response.SearchResultVO;
import com.kb.intelligence.entity.KnCredential;
import com.kb.intelligence.entity.KnDependency;
import com.kb.intelligence.entity.KnDomain;
import com.kb.intelligence.entity.KnHost;
import com.kb.intelligence.entity.KnPort;
import com.kb.intelligence.entity.KnService;

import java.util.List;
import java.util.Map;

public interface KnowledgeQueryService {

    Page<DocIndexVO> listDocs(String docType, String category, String tag, int page, int size);

    DocIndexVO getDocMeta(Long docId);

    DocEntitiesVO getDocEntities(Long docId);

    List<DocEntitiesVO.HostVO> listHosts(String ip, String name, String role);

    List<DocEntitiesVO.ServiceVO> listServices(Long hostId, String name);

    List<DocEntitiesVO.CommandVO> listCommands(Long docId, String category, String riskLevel);

    List<DocEntitiesVO.TimelineVO> listTimelines(Long docId, String severity, String eventType);

    List<DocEntitiesVO.PortVO> listPorts(Long hostId, Integer exposed);

    List<DocEntitiesVO.CredentialVO> listCredentials(Long hostId, String credType);

    List<DocEntitiesVO.DomainVO> listDomains(String status);

    List<KnPort> listAllPorts();

    List<KnCredential> listAllCredentials();

    List<KnDomain> listAllDomains();

    List<KnDependency> listAllDependencies();

    List<KnHost> listAllHosts();

    List<KnService> listAllServices();

    DocContentVO getDocContent(Long docId);

    List<SearchResultVO> search(String query, List<String> docTypes, List<String> tags, int page, int size);

    Map<String, Object> getStats();
}
