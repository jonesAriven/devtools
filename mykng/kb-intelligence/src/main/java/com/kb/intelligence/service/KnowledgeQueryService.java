package com.kb.intelligence.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.intelligence.dto.response.DocContentVO;
import com.kb.intelligence.dto.response.DocEntitiesVO;
import com.kb.intelligence.dto.response.DocIndexVO;
import com.kb.intelligence.dto.response.SearchResultVO;

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

    DocContentVO getDocContent(Long docId);

    List<SearchResultVO> search(String query, List<String> docTypes, List<String> tags, int page, int size);

    Map<String, Object> getStats();
}
