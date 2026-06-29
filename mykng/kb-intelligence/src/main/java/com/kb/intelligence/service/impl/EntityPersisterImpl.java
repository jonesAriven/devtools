package com.kb.intelligence.service.impl;

import com.kb.intelligence.entity.*;
import com.kb.intelligence.mapper.*;
import com.kb.intelligence.mongo.ContentStorage;
import com.kb.intelligence.mongo.doc.KnContent;
import com.kb.intelligence.parser.ParseResult;
import com.kb.intelligence.service.EntityPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private final KnDocMapper docMapper;
    private final KnHostMapper hostMapper;
    private final KnServiceMapper serviceMapper;
    private final KnPortMapper portMapper;
    private final KnCredentialMapper credentialMapper;
    private final KnDomainMapper domainMapper;
    private final KnDependencyMapper dependencyMapper;
    private final KnCommandMapper commandMapper;
    private final KnTimelineMapper timelineMapper;
    private final KnDocEntityRefMapper docEntityRefMapper;
    private final ContentStorage contentStorage;

    @Override
    @Transactional
    public Long persist(ParseResult result) {
        KnDoc doc = result.getDocMeta();
        Long docId;

        // 优先按 source_id 去重（source_id 与挂载点无关，更稳定）
        // fallback 到 file_path（兼容旧数据）
        KnDoc existing = null;
        if (doc.getSourceId() != null && !doc.getSourceId().isEmpty()) {
            existing = docMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnDoc>()
                            .eq(KnDoc::getSourceId, doc.getSourceId())
                            .last("LIMIT 1")
            );
        }
        if (existing == null) {
            existing = docMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnDoc>()
                            .eq(KnDoc::getFilePath, doc.getFilePath())
                            .last("LIMIT 1")
            );
        }

        if (existing != null) {
            docId = existing.getId();
            deleteExistingData(docId);
            doc.setId(docId);
            docMapper.updateById(doc);
        } else {
            docMapper.insert(doc);
            docId = doc.getId();
        }

        Map<Long, Long> hostIdMap = persistHosts(result.getHosts());
        Map<Long, Long> serviceIdMap = persistServices(result.getServices(), hostIdMap);
        persistPorts(result.getPorts(), hostIdMap, serviceIdMap);
        persistCredentials(result.getCredentials(), hostIdMap, serviceIdMap);
        persistDomains(result.getDomains(), hostIdMap);
        persistDependencies(result.getDependencies(), hostIdMap, serviceIdMap);
        persistCommands(result.getCommands(), docId);
        persistTimelines(result.getTimelines(), docId);
        persistContent(result.getContent(), docId);
        persistDocRefs(docId, hostIdMap, serviceIdMap);

        int entityCount = hostIdMap.size() + serviceIdMap.size();
        doc.setId(docId);
        doc.setEntityCount(entityCount);
        doc.setCommandCount(result.getCommands().size());
        docMapper.updateById(doc);

        log.info("文档持久化完成 docId={}, hosts={}, services={}, commands={}, timelines={}",
                docId, hostIdMap.size(), serviceIdMap.size(), result.getCommands().size(), result.getTimelines().size());
        return docId;
    }

    private void deleteExistingData(Long docId) {
        commandMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnCommand>()
                .eq(KnCommand::getDocId, docId));
        timelineMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnTimeline>()
                .eq(KnTimeline::getDocId, docId));
        docEntityRefMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnDocEntityRef>()
                .eq(KnDocEntityRef::getDocId, docId));
        contentStorage.deleteByDocId(docId);
    }

    private Map<Long, Long> persistHosts(List<KnHost> hosts) {
        Map<Long, Long> idMap = new HashMap<>();
        for (int i = 0; i < hosts.size(); i++) {
            KnHost h = hosts.get(i);

            KnHost existing = null;
            if (h.getIp() != null) {
                existing = hostMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnHost>()
                                .eq(KnHost::getIp, h.getIp())
                                .last("LIMIT 1")
                );
            }

            if (existing != null) {
                idMap.put((long) (i + 1), existing.getId());
                mergeHost(existing, h);
                hostMapper.updateById(existing);
            } else {
                hostMapper.insert(h);
                idMap.put((long) (i + 1), h.getId());
            }
        }
        return idMap;
    }

    private void mergeHost(KnHost existing, KnHost incoming) {
        if (isEmpty(existing.getName()) && !isEmpty(incoming.getName())) existing.setName(incoming.getName());
        if (isEmpty(existing.getTailscaleIp()) && !isEmpty(incoming.getTailscaleIp())) existing.setTailscaleIp(incoming.getTailscaleIp());
        if (isEmpty(existing.getUsername()) && !isEmpty(incoming.getUsername())) existing.setUsername(incoming.getUsername());
        if (isEmpty(existing.getPasswordEncrypted()) && !isEmpty(incoming.getPasswordEncrypted())) existing.setPasswordEncrypted(incoming.getPasswordEncrypted());
        if (isEmpty(existing.getRole()) && !isEmpty(incoming.getRole())) existing.setRole(incoming.getRole());
        if (isEmpty(existing.getOsType()) && !isEmpty(incoming.getOsType())) existing.setOsType(incoming.getOsType());
        if (isEmpty(existing.getRemark()) && !isEmpty(incoming.getRemark())) existing.setRemark(incoming.getRemark());
        if (existing.getSshPort() == null || existing.getSshPort() == 0) existing.setSshPort(incoming.getSshPort() != null ? incoming.getSshPort() : 22);
    }

    private Map<Long, Long> persistServices(List<KnService> services, Map<Long, Long> hostIdMap) {
        Map<Long, Long> idMap = new HashMap<>();
        for (int i = 0; i < services.size(); i++) {
            KnService s = services.get(i);
            if (s.getHostId() != null && hostIdMap.containsKey(s.getHostId())) {
                s.setHostId(hostIdMap.get(s.getHostId()));
            }

            KnService existing = null;
            if (s.getName() != null && s.getHostId() != null) {
                existing = serviceMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnService>()
                                .eq(KnService::getName, s.getName())
                                .eq(KnService::getHostId, s.getHostId())
                                .last("LIMIT 1")
                );
            }

            if (existing != null) {
                idMap.put((long) (i + 1), existing.getId());
                mergeService(existing, s);
                serviceMapper.updateById(existing);
            } else {
                serviceMapper.insert(s);
                idMap.put((long) (i + 1), s.getId());
            }
        }
        return idMap;
    }

    private void mergeService(KnService existing, KnService incoming) {
        if (isEmpty(existing.getVersion()) && !isEmpty(incoming.getVersion())) existing.setVersion(incoming.getVersion());
        if (isEmpty(existing.getServiceType()) && !isEmpty(incoming.getServiceType())) existing.setServiceType(incoming.getServiceType());
        if (isEmpty(existing.getInstallPath()) && !isEmpty(incoming.getInstallPath())) existing.setInstallPath(incoming.getInstallPath());
        if (isEmpty(existing.getRemark()) && !isEmpty(incoming.getRemark())) existing.setRemark(incoming.getRemark());
    }

    private void persistPorts(List<KnPort> ports, Map<Long, Long> hostIdMap, Map<Long, Long> serviceIdMap) {
        for (KnPort p : ports) {
            if (p.getHostId() != null && hostIdMap.containsKey(p.getHostId())) {
                p.setHostId(hostIdMap.get(p.getHostId()));
            }
            if (p.getServiceId() != null && serviceIdMap.containsKey(p.getServiceId())) {
                p.setServiceId(serviceIdMap.get(p.getServiceId()));
            }
            portMapper.insert(p);
        }
    }

    private void persistCredentials(List<KnCredential> creds, Map<Long, Long> hostIdMap, Map<Long, Long> serviceIdMap) {
        for (KnCredential c : creds) {
            if (c.getHostId() != null && hostIdMap.containsKey(c.getHostId())) {
                c.setHostId(hostIdMap.get(c.getHostId()));
            }
            if (c.getServiceId() != null && serviceIdMap.containsKey(c.getServiceId())) {
                c.setServiceId(serviceIdMap.get(c.getServiceId()));
            }
            credentialMapper.insert(c);
        }
    }

    private void persistDomains(List<KnDomain> domains, Map<Long, Long> hostIdMap) {
        for (KnDomain d : domains) {
            if (d.getTargetHostId() != null && hostIdMap.containsKey(d.getTargetHostId())) {
                d.setTargetHostId(hostIdMap.get(d.getTargetHostId()));
            }
            domainMapper.insert(d);
        }
    }

    private void persistDependencies(List<KnDependency> deps, Map<Long, Long> hostIdMap, Map<Long, Long> serviceIdMap) {
        for (KnDependency d : deps) {
            if ("host".equals(d.getFromType()) && d.getFromId() != null && hostIdMap.containsKey(d.getFromId())) {
                d.setFromId(hostIdMap.get(d.getFromId()));
            }
            if ("service".equals(d.getFromType()) && d.getFromId() != null && serviceIdMap.containsKey(d.getFromId())) {
                d.setFromId(serviceIdMap.get(d.getFromId()));
            }
            if ("host".equals(d.getToType()) && d.getToId() != null && hostIdMap.containsKey(d.getToId())) {
                d.setToId(hostIdMap.get(d.getToId()));
            }
            if ("service".equals(d.getToType()) && d.getToId() != null && serviceIdMap.containsKey(d.getToId())) {
                d.setToId(serviceIdMap.get(d.getToId()));
            }
            dependencyMapper.insert(d);
        }
    }

    private void persistCommands(List<KnCommand> commands, Long docId) {
        for (KnCommand c : commands) {
            c.setDocId(docId);
            commandMapper.insert(c);
        }
    }

    private void persistTimelines(List<KnTimeline> timelines, Long docId) {
        for (KnTimeline t : timelines) {
            t.setDocId(docId);
            timelineMapper.insert(t);
        }
    }

    private void persistContent(KnContent content, Long docId) {
        if (content == null) return;
        content.setDocId(docId);
        contentStorage.deleteByDocId(docId);
        contentStorage.save(content);
    }

    private void persistDocRefs(Long docId, Map<Long, Long> hostIdMap, Map<Long, Long> serviceIdMap) {
        for (Long hostDbId : hostIdMap.values()) {
            KnDocEntityRef ref = new KnDocEntityRef();
            ref.setDocId(docId);
            ref.setEntityType("host");
            ref.setEntityId(hostDbId);
            ref.setConfidence(80);
            docEntityRefMapper.insert(ref);
        }
        for (Long svcDbId : serviceIdMap.values()) {
            KnDocEntityRef ref = new KnDocEntityRef();
            ref.setDocId(docId);
            ref.setEntityType("service");
            ref.setEntityId(svcDbId);
            ref.setConfidence(80);
            docEntityRefMapper.insert(ref);
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
