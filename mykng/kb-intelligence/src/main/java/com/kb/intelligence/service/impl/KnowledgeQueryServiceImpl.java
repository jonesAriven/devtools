package com.kb.intelligence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.intelligence.dto.response.*;
import com.kb.intelligence.entity.*;
import com.kb.intelligence.mapper.*;
import com.kb.intelligence.mongo.ContentStorage;
import com.kb.intelligence.mongo.doc.KnContent;
import com.kb.intelligence.service.KnowledgeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQueryServiceImpl implements KnowledgeQueryService {

    private final KnDocMapper docMapper;
    private final KnHostMapper hostMapper;
    private final KnServiceMapper serviceMapper;
    private final KnPortMapper portMapper;
    private final KnCredentialMapper credentialMapper;
    private final KnDomainMapper domainMapper;
    private final KnCommandMapper commandMapper;
    private final KnTimelineMapper timelineMapper;
    private final KnDocEntityRefMapper docEntityRefMapper;
    private final ContentStorage contentStorage;

    @Override
    public Page<DocIndexVO> listDocs(String docType, String category, String tag, int page, int size) {
        LambdaQueryWrapper<KnDoc> wrapper = new LambdaQueryWrapper<>();
        if (docType != null && !docType.isBlank()) wrapper.eq(KnDoc::getDocType, docType.toUpperCase());
        if (category != null && !category.isBlank()) wrapper.like(KnDoc::getCategory, category);
        if (tag != null && !tag.isBlank()) wrapper.like(KnDoc::getTags, tag);
        wrapper.orderByDesc(KnDoc::getUpdatedAt);

        Page<KnDoc> result = docMapper.selectPage(new Page<>(page, size), wrapper);

        Page<DocIndexVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toIndexVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DocIndexVO getDocMeta(Long docId) {
        KnDoc doc = docMapper.selectById(docId);
        return doc != null ? toIndexVO(doc) : null;
    }

    @Override
    public DocEntitiesVO getDocEntities(Long docId) {
        List<KnDocEntityRef> refs = docEntityRefMapper.selectList(
                new LambdaQueryWrapper<KnDocEntityRef>().eq(KnDocEntityRef::getDocId, docId)
        );

        Set<Long> hostIds = new HashSet<>();
        Set<Long> serviceIds = new HashSet<>();
        for (KnDocEntityRef ref : refs) {
            if ("host".equals(ref.getEntityType())) hostIds.add(ref.getEntityId());
            if ("service".equals(ref.getEntityType())) serviceIds.add(ref.getEntityId());
        }

        DocEntitiesVO vo = new DocEntitiesVO();
        vo.setDocId(docId);
        KnDoc doc = docMapper.selectById(docId);
        if (doc != null) vo.setTitle(doc.getTitle());

        if (!hostIds.isEmpty()) {
            List<KnHost> hosts = hostMapper.selectBatchIds(hostIds);
            vo.setHosts(hosts.stream().map(this::toHostVO).collect(Collectors.toList()));

            List<KnPort> ports = portMapper.selectList(
                    new LambdaQueryWrapper<KnPort>().in(KnPort::getHostId, hostIds)
            );
            vo.setPorts(ports.stream().map(this::toPortVO).collect(Collectors.toList()));

            List<KnCredential> creds = credentialMapper.selectList(
                    new LambdaQueryWrapper<KnCredential>().in(KnCredential::getHostId, hostIds)
            );
            vo.setCredentials(creds.stream().map(this::toCredentialVO).collect(Collectors.toList()));
        }

        if (!serviceIds.isEmpty()) {
            List<KnService> services = serviceMapper.selectBatchIds(serviceIds);
            vo.setServices(services.stream().map(this::toServiceVO).collect(Collectors.toList()));
        }

        List<KnDomain> domains = domainMapper.selectList(
                new LambdaQueryWrapper<KnDomain>().eq(!hostIds.isEmpty(), KnDomain::getTargetHostId, hostIds.isEmpty() ? null : hostIds.iterator().next())
        );
        if (domains.isEmpty() && !hostIds.isEmpty()) {
            domains = domainMapper.selectList(new LambdaQueryWrapper<KnDomain>().in(KnDomain::getTargetHostId, hostIds));
        }
        vo.setDomains(domains.stream().map(this::toDomainVO).collect(Collectors.toList()));

        List<KnCommand> commands = commandMapper.selectList(
                new LambdaQueryWrapper<KnCommand>().eq(KnCommand::getDocId, docId)
        );
        vo.setCommands(commands.stream().map(this::toCommandVO).collect(Collectors.toList()));

        vo.setTotalEntities((vo.getHosts() != null ? vo.getHosts().size() : 0)
                + (vo.getServices() != null ? vo.getServices().size() : 0)
                + (vo.getPorts() != null ? vo.getPorts().size() : 0)
                + (vo.getCredentials() != null ? vo.getCredentials().size() : 0));

        return vo;
    }

    @Override
    public List<DocEntitiesVO.HostVO> listHosts(String ip, String name, String role) {
        LambdaQueryWrapper<KnHost> wrapper = new LambdaQueryWrapper<>();
        if (ip != null && !ip.isBlank()) wrapper.like(KnHost::getIp, ip);
        if (name != null && !name.isBlank()) wrapper.like(KnHost::getName, name);
        if (role != null && !role.isBlank()) wrapper.like(KnHost::getRole, role);
        wrapper.orderByAsc(KnHost::getIp);
        return hostMapper.selectList(wrapper).stream().map(this::toHostVO).collect(Collectors.toList());
    }

    @Override
    public List<DocEntitiesVO.ServiceVO> listServices(Long hostId, String name) {
        LambdaQueryWrapper<KnService> wrapper = new LambdaQueryWrapper<>();
        if (hostId != null) wrapper.eq(KnService::getHostId, hostId);
        if (name != null && !name.isBlank()) wrapper.like(KnService::getName, name);
        return serviceMapper.selectList(wrapper).stream().map(this::toServiceVO).collect(Collectors.toList());
    }

    @Override
    public List<DocEntitiesVO.CommandVO> listCommands(Long docId, String category, String riskLevel) {
        LambdaQueryWrapper<KnCommand> wrapper = new LambdaQueryWrapper<>();
        if (docId != null) wrapper.eq(KnCommand::getDocId, docId);
        if (category != null && !category.isBlank()) wrapper.eq(KnCommand::getCategory, category);
        if (riskLevel != null && !riskLevel.isBlank()) wrapper.eq(KnCommand::getRiskLevel, riskLevel);
        wrapper.orderByAsc(KnCommand::getCategory);
        return commandMapper.selectList(wrapper).stream().map(this::toCommandVO).collect(Collectors.toList());
    }

    @Override
    public List<DocEntitiesVO.TimelineVO> listTimelines(Long docId, String severity, String eventType) {
        LambdaQueryWrapper<KnTimeline> wrapper = new LambdaQueryWrapper<>();
        if (docId != null) wrapper.eq(KnTimeline::getDocId, docId);
        if (severity != null && !severity.isBlank()) wrapper.eq(KnTimeline::getSeverity, severity);
        if (eventType != null && !eventType.isBlank()) wrapper.eq(KnTimeline::getEventType, eventType);
        wrapper.orderByDesc(KnTimeline::getEventTime);
        return timelineMapper.selectList(wrapper).stream().map(this::toTimelineVO).collect(Collectors.toList());
    }

    @Override
    public DocContentVO getDocContent(Long docId) {
        KnDoc doc = docMapper.selectById(docId);
        if (doc == null) return null;

        Optional<KnContent> contentOpt = contentStorage.findByDocId(docId);
        DocContentVO vo = new DocContentVO();
        vo.setDocId(docId);
        vo.setTitle(doc.getTitle());

        if (contentOpt.isPresent()) {
            KnContent c = contentOpt.get();
            vo.setPlainText(c.getPlainText());
            vo.setWordCount(c.getWordCount());
            vo.setSections(c.getSections().stream().map(s -> {
                DocContentVO.SectionVO svo = new DocContentVO.SectionVO();
                svo.setTitle(s.getTitle());
                svo.setLevel(s.getLevel());
                svo.setContent(s.getContent());
                svo.setWordCount(s.getWordCount());
                return svo;
            }).collect(Collectors.toList()));
        }
        return vo;
    }

    @Override
    public List<SearchResultVO> search(String query, List<String> docTypes, List<String> tags, int page, int size) {
        List<SearchResultVO> results = new ArrayList<>();

        LambdaQueryWrapper<KnDoc> wrapper = new LambdaQueryWrapper<>();
        if (query != null && !query.isBlank()) {
            wrapper.and(w -> w.like(KnDoc::getTitle, query)
                    .or().like(KnDoc::getSummary, query)
                    .or().like(KnDoc::getTags, query)
                    .or().like(KnDoc::getFilePath, query));
        }
        if (docTypes != null && !docTypes.isEmpty()) {
            wrapper.in(KnDoc::getDocType, docTypes.stream().map(String::toUpperCase).collect(Collectors.toList()));
        }
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                wrapper.like(KnDoc::getTags, tag);
            }
        }
        wrapper.orderByDesc(KnDoc::getUpdatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));

        List<KnDoc> docs = docMapper.selectList(wrapper);
        for (KnDoc doc : docs) {
            SearchResultVO r = new SearchResultVO();
            r.setDocId(doc.getId());
            r.setDocTitle(doc.getTitle());
            r.setDocType(doc.getDocType());
            r.setCategory(doc.getCategory());
            r.setHighlight(doc.getSummary());
            r.setScore(1.0f);
            results.add(r);
        }

        return results;
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("docCount", docMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("hostCount", hostMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("serviceCount", serviceMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("portCount", portMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("credentialCount", credentialMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("commandCount", commandMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("domainCount", domainMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("timelineCount", timelineMapper.selectCount(new LambdaQueryWrapper<>()));

        List<Map<String, Object>> byType = new ArrayList<>();
        for (String type : new String[]{"TABLE", "PLAN", "TIMELINE", "GRAPH", "RULE", "GENERAL"}) {
            long count = docMapper.selectCount(new LambdaQueryWrapper<KnDoc>().eq(KnDoc::getDocType, type));
            if (count > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("docType", type);
                m.put("count", count);
                byType.add(m);
            }
        }
        stats.put("byType", byType);
        return stats;
    }

    private DocIndexVO toIndexVO(KnDoc d) {
        DocIndexVO vo = new DocIndexVO();
        vo.setId(d.getId());
        vo.setTitle(d.getTitle());
        vo.setSourceId(d.getSourceId());
        vo.setFilePath(d.getFilePath());
        vo.setDocType(d.getDocType());
        vo.setCategory(d.getCategory());
        vo.setTags(d.getTags());
        vo.setSummary(d.getSummary());
        vo.setEntityCount(d.getEntityCount());
        vo.setCommandCount(d.getCommandCount());
        vo.setSectionCount(d.getSectionCount());
        vo.setWordCount(d.getWordCount());
        vo.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        vo.setUpdatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
        return vo;
    }

    private DocEntitiesVO.HostVO toHostVO(KnHost h) {
        DocEntitiesVO.HostVO vo = new DocEntitiesVO.HostVO();
        vo.setId(h.getId());
        vo.setName(h.getName());
        vo.setIp(h.getIp());
        vo.setTailscaleIp(h.getTailscaleIp());
        vo.setSshPort(h.getSshPort());
        vo.setUsername(h.getUsername());
        vo.setRole(h.getRole());
        vo.setOsType(h.getOsType());
        vo.setStatus(h.getStatus());
        return vo;
    }

    private DocEntitiesVO.ServiceVO toServiceVO(KnService s) {
        DocEntitiesVO.ServiceVO vo = new DocEntitiesVO.ServiceVO();
        vo.setId(s.getId());
        vo.setHostId(s.getHostId());
        vo.setName(s.getName());
        vo.setServiceType(s.getServiceType());
        vo.setVersion(s.getVersion());
        vo.setStatus(s.getStatus());
        return vo;
    }

    private DocEntitiesVO.PortVO toPortVO(KnPort p) {
        DocEntitiesVO.PortVO vo = new DocEntitiesVO.PortVO();
        vo.setId(p.getId());
        vo.setHostId(p.getHostId());
        vo.setServiceId(p.getServiceId());
        vo.setPort(p.getPort());
        vo.setProtocol(p.getProtocol());
        vo.setAccessUrl(p.getAccessUrl());
        vo.setExposed(p.getExposed());
        return vo;
    }

    private DocEntitiesVO.CredentialVO toCredentialVO(KnCredential c) {
        DocEntitiesVO.CredentialVO vo = new DocEntitiesVO.CredentialVO();
        vo.setId(c.getId());
        vo.setHostId(c.getHostId());
        vo.setCredType(c.getCredType());
        vo.setUsername(c.getUsername());
        String pwd = c.getPasswordEncrypted();
        vo.setPasswordHint(pwd != null && pwd.length() > 2 ? pwd.substring(0, 1) + "***" + pwd.substring(pwd.length() - 1) : null);
        return vo;
    }

    private DocEntitiesVO.DomainVO toDomainVO(KnDomain d) {
        DocEntitiesVO.DomainVO vo = new DocEntitiesVO.DomainVO();
        vo.setId(d.getId());
        vo.setDomain(d.getDomain());
        vo.setSubDomain(d.getSubDomain());
        vo.setTargetHostId(d.getTargetHostId());
        vo.setTargetPort(d.getTargetPort());
        vo.setStatus(d.getStatus());
        return vo;
    }

    private DocEntitiesVO.CommandVO toCommandVO(KnCommand c) {
        DocEntitiesVO.CommandVO vo = new DocEntitiesVO.CommandVO();
        vo.setId(c.getId());
        vo.setCommand(c.getCommand());
        vo.setDescription(c.getDescription());
        vo.setCategory(c.getCategory());
        vo.setRiskLevel(c.getRiskLevel());
        vo.setOsType(c.getOsType());
        return vo;
    }

    private DocEntitiesVO.TimelineVO toTimelineVO(KnTimeline t) {
        DocEntitiesVO.TimelineVO vo = new DocEntitiesVO.TimelineVO();
        vo.setId(t.getId());
        vo.setDocId(t.getDocId());
        vo.setEventTime(t.getEventTime());
        vo.setEventType(t.getEventType());
        vo.setTitle(t.getTitle());
        vo.setDescription(t.getDescription());
        vo.setSeverity(t.getSeverity());
        vo.setStatus(t.getStatus());
        vo.setSolution(t.getSolution());
        return vo;
    }
}
