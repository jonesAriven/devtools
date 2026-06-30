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
        Set<String> processedKeys = new HashSet<>();
        for (int i = 0; i < services.size(); i++) {
            KnService s = services.get(i);
            if (s.getHostId() != null && hostIdMap.containsKey(s.getHostId())) {
                s.setHostId(hostIdMap.get(s.getHostId()));
            }

            // 批次内去重 key = name + hostId（hostId 可能为 null）
            String dedupKey = (s.getName() == null ? "" : s.getName().toLowerCase()) + "@" + (s.getHostId() == null ? "" : s.getHostId());
            if (processedKeys.contains(dedupKey)) {
                idMap.put((long) (i + 1), null);
                continue;
            }
            processedKeys.add(dedupKey);

            KnService existing = null;
            if (s.getName() != null) {
                // 有 hostId 按 name+hostId 精确匹配
                if (s.getHostId() != null) {
                    existing = serviceMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnService>()
                                    .eq(KnService::getName, s.getName())
                                    .eq(KnService::getHostId, s.getHostId())
                                    .last("LIMIT 1")
                    );
                }
                // 无 hostId 按 name 全局匹配（避免孤立服务无限增长）
                if (existing == null) {
                    existing = serviceMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnService>()
                                    .eq(KnService::getName, s.getName())
                                    .and(w -> w.isNull(KnService::getHostId).or().eq(KnService::getHostId, 0))
                                    .last("LIMIT 1")
                    );
                    // 如果 incoming 有 hostId 但 existing 没有，不要复用无 hostId 的记录
                    if (existing != null && s.getHostId() != null) {
                        existing = null;
                    }
                }
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
        Set<String> processedKeys = new HashSet<>();
        for (KnPort p : ports) {
            if (p.getHostId() != null && hostIdMap.containsKey(p.getHostId())) {
                p.setHostId(hostIdMap.get(p.getHostId()));
            }
            if (p.getServiceId() != null && serviceIdMap.containsKey(p.getServiceId())) {
                p.setServiceId(serviceIdMap.get(p.getServiceId()));
            }
            // 批次内去重
            String dedupKey = (p.getHostId() == null ? "" : p.getHostId()) + ":" + (p.getPort() == null ? "" : p.getPort());
            if (processedKeys.contains(dedupKey)) continue;
            processedKeys.add(dedupKey);

            // 数据库去重：按 hostId + port 查找 existing
            if (p.getHostId() != null && p.getPort() != null) {
                KnPort existing = portMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnPort>()
                                .eq(KnPort::getHostId, p.getHostId())
                                .eq(KnPort::getPort, p.getPort())
                                .last("LIMIT 1")
                );
                if (existing != null) {
                    // 合并：补充缺失字段
                    if (isEmpty(existing.getProtocol()) && !isEmpty(p.getProtocol())) {
                        existing.setProtocol(p.getProtocol());
                        portMapper.updateById(existing);
                    }
                    continue;
                }
            }
            portMapper.insert(p);
        }
    }

    private void persistCredentials(List<KnCredential> creds, Map<Long, Long> hostIdMap, Map<Long, Long> serviceIdMap) {
        Set<String> processedKeys = new HashSet<>();
        for (KnCredential c : creds) {
            if (c.getHostId() != null && hostIdMap.containsKey(c.getHostId())) {
                c.setHostId(hostIdMap.get(c.getHostId()));
            }
            if (c.getServiceId() != null && serviceIdMap.containsKey(c.getServiceId())) {
                c.setServiceId(serviceIdMap.get(c.getServiceId()));
            }
            // 批次内去重：hostId + username + credType
            String dedupKey = (c.getHostId() == null ? "" : c.getHostId()) + "|" +
                    (c.getUsername() == null ? "" : c.getUsername()) + "|" +
                    (c.getCredType() == null ? "" : c.getCredType());
            if (processedKeys.contains(dedupKey)) continue;
            processedKeys.add(dedupKey);

            // 数据库去重：按 hostId + username + credType 查找 existing
            KnCredential existing = null;
            if (c.getHostId() != null) {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnCredential> qw =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnCredential>()
                                .eq(KnCredential::getHostId, c.getHostId());
                if (c.getUsername() != null && !c.getUsername().isEmpty()) {
                    qw.eq(KnCredential::getUsername, c.getUsername());
                } else {
                    qw.isNull(KnCredential::getUsername);
                }
                if (c.getCredType() != null && !c.getCredType().isEmpty()) {
                    qw.eq(KnCredential::getCredType, c.getCredType());
                }
                existing = credentialMapper.selectOne(qw.last("LIMIT 1"));
            }
            if (existing != null) {
                mergeCredential(existing, c);
                credentialMapper.updateById(existing);
            } else {
                credentialMapper.insert(c);
            }
        }
    }

    private void mergeCredential(KnCredential existing, KnCredential incoming) {
        if (isEmpty(existing.getUsername()) && !isEmpty(incoming.getUsername())) existing.setUsername(incoming.getUsername());
        if (isEmpty(existing.getPasswordEncrypted()) && !isEmpty(incoming.getPasswordEncrypted())) existing.setPasswordEncrypted(incoming.getPasswordEncrypted());
        if (isEmpty(existing.getCredType()) && !isEmpty(incoming.getCredType())) existing.setCredType(incoming.getCredType());
    }

    private void persistDomains(List<KnDomain> domains, Map<Long, Long> hostIdMap) {
        Set<String> processedDomains = new HashSet<>();
        for (KnDomain d : domains) {
            if (d.getTargetHostId() != null && hostIdMap.containsKey(d.getTargetHostId())) {
                d.setTargetHostId(hostIdMap.get(d.getTargetHostId()));
            }
            // 批次内去重
            String domainKey = d.getDomain() == null ? "" : d.getDomain().toLowerCase();
            if (processedDomains.contains(domainKey)) continue;
            processedDomains.add(domainKey);

            // 数据库去重：按 domain 查找 existing
            KnDomain existing = null;
            if (d.getDomain() != null) {
                existing = domainMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnDomain>()
                                .eq(KnDomain::getDomain, d.getDomain())
                                .last("LIMIT 1")
                );
            }
            if (existing != null) {
                mergeDomain(existing, d);
                domainMapper.updateById(existing);
            } else {
                domainMapper.insert(d);
            }
        }
    }

    private void mergeDomain(KnDomain existing, KnDomain incoming) {
        if (isEmpty(existing.getSubDomain()) && !isEmpty(incoming.getSubDomain())) existing.setSubDomain(incoming.getSubDomain());
        if (existing.getTargetHostId() == null && incoming.getTargetHostId() != null) existing.setTargetHostId(incoming.getTargetHostId());
        if (existing.getTargetPort() == null && incoming.getTargetPort() != null) existing.setTargetPort(incoming.getTargetPort());
        if (isEmpty(existing.getTargetService()) && !isEmpty(incoming.getTargetService())) existing.setTargetService(incoming.getTargetService());
        if (isEmpty(existing.getDnsType()) && !isEmpty(incoming.getDnsType())) existing.setDnsType(incoming.getDnsType());
        if (isEmpty(existing.getStatus()) && !isEmpty(incoming.getStatus())) existing.setStatus(incoming.getStatus());
        if (isEmpty(existing.getRemark()) && !isEmpty(incoming.getRemark())) existing.setRemark(incoming.getRemark());
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
