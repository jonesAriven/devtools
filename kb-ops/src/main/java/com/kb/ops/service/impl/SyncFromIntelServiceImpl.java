package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.result.Result;
import com.kb.ops.dto.SyncFromIntelRequest;
import com.kb.ops.dto.SyncFromIntelResult;
import com.kb.ops.entity.*;
import com.kb.ops.mapper.*;
import com.kb.ops.service.SyncFromIntelService;
import com.kb.ops.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class SyncFromIntelServiceImpl implements SyncFromIntelService {

    private final HostMapper hostMapper;
    private final OpsServiceMapper serviceMapper;
    private final PortMapper portMapper;
    private final CredentialMapper credentialMapper;
    private final DomainMapper domainMapper;
    private final DependencyMapper dependencyMapper;
    private final CryptoUtil cryptoUtil;

    private final String intelBaseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SyncFromIntelServiceImpl(
            HostMapper hostMapper,
            OpsServiceMapper serviceMapper,
            PortMapper portMapper,
            CredentialMapper credentialMapper,
            DomainMapper domainMapper,
            DependencyMapper dependencyMapper,
            CryptoUtil cryptoUtil,
            @Value("${kb.feign.intelligence-url:http://kb-intelligence:8086}") String intelBaseUrl) {
        this.hostMapper = hostMapper;
        this.serviceMapper = serviceMapper;
        this.portMapper = portMapper;
        this.credentialMapper = credentialMapper;
        this.domainMapper = domainMapper;
        this.dependencyMapper = dependencyMapper;
        this.cryptoUtil = cryptoUtil;
        this.intelBaseUrl = intelBaseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public SyncFromIntelResult syncFromIntelligence(SyncFromIntelRequest request) {
        long start = System.currentTimeMillis();
        SyncFromIntelResult result = new SyncFromIntelResult();
        boolean override = request.isOverride();
        Set<String> types = request.getEntityTypes() != null && !request.getEntityTypes().isEmpty()
                ? new HashSet<>(request.getEntityTypes())
                : new HashSet<>(Arrays.asList("HOST", "SERVICE", "PORT", "CREDENTIAL", "DOMAIN", "DEPENDENCY"));

        try {
            Map<Long, String> intelHostIdToIp = new HashMap<>();
            Map<String, Long> ipToOpsHostId = new HashMap<>();
            Map<String, Long> intelSvcNameToOpsSvcId = new HashMap<>();
            Map<Long, String> intelSvcIdToName = new HashMap<>();

            if (types.contains("HOST")) {
                syncHosts(override, result.getHost(), intelHostIdToIp, ipToOpsHostId);
            }
            if (types.contains("SERVICE")) {
                syncServices(override, result.getService(), intelHostIdToIp, ipToOpsHostId,
                        intelSvcNameToOpsSvcId, intelSvcIdToName);
            }
            if (types.contains("PORT")) {
                syncPorts(override, result.getPort(), intelHostIdToIp, ipToOpsHostId,
                        intelSvcIdToName, intelSvcNameToOpsSvcId);
            }
            if (types.contains("CREDENTIAL")) {
                syncCredentials(override, result.getCredential(), intelHostIdToIp, ipToOpsHostId,
                        intelSvcIdToName, intelSvcNameToOpsSvcId);
            }
            if (types.contains("DOMAIN")) {
                syncDomains(override, result.getDomain());
            }
            if (types.contains("DEPENDENCY")) {
                syncDependencies(override, result.getDependency(), intelSvcIdToName, intelSvcNameToOpsSvcId);
            }
        } catch (Exception e) {
            log.error("[知识引擎同步] 同步失败: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - start);
        log.info("[知识引擎同步] 完成，耗时 {}ms", result.getDurationMs());
        return result;
    }

    // ============ 主机同步 ============
    private void syncHosts(boolean override, SyncFromIntelResult.SyncStats stats,
                           Map<Long, String> intelHostIdToIp, Map<String, Long> ipToOpsHostId) {
        List<Map<String, Object>> hosts = fetchList("/intelligence/machine/internal/hosts");
        stats.setTotal(hosts.size());
        log.info("[同步主机] 从知识引擎获取 {} 条", hosts.size());

        for (Map<String, Object> h : hosts) {
            Long intelId = longVal(h.get("id"));
            String ip = strVal(h.get("ip"));
            if (intelId != null && ip != null) {
                intelHostIdToIp.put(intelId, ip);
            }
        }

        for (Map<String, Object> h : hosts) {
            try {
                String ip = strVal(h.get("ip"));
                if (ip == null || ip.isBlank()) {
                    stats.incrementFailed();
                    continue;
                }

                Host exist = hostMapper.selectOne(new LambdaQueryWrapper<Host>().eq(Host::getIp, ip));
                if (exist != null) {
                    ipToOpsHostId.put(ip, exist.getId());
                    if (!override) {
                        stats.incrementSkipped();
                        continue;
                    }
                }

                Host host = exist != null ? exist : new Host();
                host.setName(defaultIfBlank(strVal(h.get("name")), ip));
                host.setIp(ip);
                host.setTailscaleIp(strVal(h.get("tailscaleIp")));
                host.setSshPort(intVal(h.get("sshPort"), 22));
                host.setUsername(strVal(h.get("username")));
                String rawPwd = strVal(h.get("passwordEncrypted"));
                if (StringUtils.hasText(rawPwd) && (exist == null || override)) {
                    host.setPasswordEncrypted(cryptoUtil.encrypt(rawPwd));
                }
                host.setRole(strVal(h.get("role")));
                host.setStatus(statusToInt(strVal(h.get("status"))));
                host.setTags(strVal(h.get("tags")));
                String remark = buildHostRemark(h);
                if (StringUtils.hasText(remark)) {
                    host.setRemark(remark);
                }

                if (exist != null) {
                    hostMapper.updateById(host);
                    stats.incrementUpdated();
                } else {
                    hostMapper.insert(host);
                    ipToOpsHostId.put(ip, host.getId());
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步主机] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ 服务同步 ============
    private void syncServices(boolean override, SyncFromIntelResult.SyncStats stats,
                              Map<Long, String> intelHostIdToIp, Map<String, Long> ipToOpsHostId,
                              Map<String, Long> intelSvcNameToOpsSvcId, Map<Long, String> intelSvcIdToName) {
        List<Map<String, Object>> services = fetchList("/intelligence/machine/internal/services");
        stats.setTotal(services.size());
        log.info("[同步服务] 从知识引擎获取 {} 条", services.size());

        for (Map<String, Object> s : services) {
            Long intelId = longVal(s.get("id"));
            String name = strVal(s.get("name"));
            if (intelId != null && name != null) {
                intelSvcIdToName.put(intelId, name);
            }
        }

        for (Map<String, Object> s : services) {
            try {
                String name = strVal(s.get("name"));
                if (name == null || name.isBlank()) {
                    stats.incrementFailed();
                    continue;
                }

                Long opsHostId = null;
                Long intelHostId = longVal(s.get("hostId"));
                if (intelHostId != null) {
                    String ip = intelHostIdToIp.get(intelHostId);
                    if (ip != null) opsHostId = ipToOpsHostId.get(ip);
                }

                OpsService exist = serviceMapper.selectOne(
                        new LambdaQueryWrapper<OpsService>().eq(OpsService::getName, name));
                if (exist != null) {
                    intelSvcNameToOpsSvcId.put(name, exist.getId());
                    if (!override) {
                        stats.incrementSkipped();
                        continue;
                    }
                }

                OpsService svc = exist != null ? exist : new OpsService();
                svc.setName(name);
                svc.setType(strVal(s.get("serviceType")));
                svc.setVersion(strVal(s.get("version")));
                if (opsHostId != null) svc.setHostId(opsHostId);
                svc.setDeployPath(strVal(s.get("installPath")));
                svc.setStatus(statusToInt(strVal(s.get("status"))));
                svc.setTags(strVal(s.get("tags")));
                svc.setRemark(strVal(s.get("remark")));

                if (exist != null) {
                    serviceMapper.updateById(svc);
                    stats.incrementUpdated();
                } else {
                    serviceMapper.insert(svc);
                    intelSvcNameToOpsSvcId.put(name, svc.getId());
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步服务] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ 端口同步 ============
    private void syncPorts(boolean override, SyncFromIntelResult.SyncStats stats,
                           Map<Long, String> intelHostIdToIp, Map<String, Long> ipToOpsHostId,
                           Map<Long, String> intelSvcIdToName, Map<String, Long> intelSvcNameToOpsSvcId) {
        List<Map<String, Object>> ports = fetchList("/intelligence/machine/internal/ports");
        stats.setTotal(ports.size());
        log.info("[同步端口] 从知识引擎获取 {} 条", ports.size());

        for (Map<String, Object> p : ports) {
            try {
                Integer portNum = intVal(p.get("port"), null);
                if (portNum == null) {
                    stats.incrementFailed();
                    continue;
                }

                Long opsHostId = null;
                Long intelHostId = longVal(p.get("hostId"));
                if (intelHostId != null) {
                    String ip = intelHostIdToIp.get(intelHostId);
                    if (ip != null) opsHostId = ipToOpsHostId.get(ip);
                }

                Long opsSvcId = null;
                Long intelSvcId = longVal(p.get("serviceId"));
                if (intelSvcId != null) {
                    String svcName = intelSvcIdToName.get(intelSvcId);
                    if (svcName != null) opsSvcId = intelSvcNameToOpsSvcId.get(svcName);
                }

                Port exist = null;
                if (opsHostId != null) {
                    exist = portMapper.selectOne(new LambdaQueryWrapper<Port>()
                            .eq(Port::getHostId, opsHostId)
                            .eq(Port::getPort, portNum));
                }
                if (exist != null && !override) {
                    stats.incrementSkipped();
                    continue;
                }

                Port port = exist != null ? exist : new Port();
                port.setPort(portNum);
                String proto = defaultIfBlank(strVal(p.get("protocol")), "TCP");
                port.setProtocol(proto.toUpperCase());
                if (opsHostId != null) port.setHostId(opsHostId);
                if (opsSvcId != null) port.setServiceId(opsSvcId);
                Integer exposed = intVal(p.get("exposed"), 0);
                port.setExposed(exposed != null && exposed == 1 ? 1 : 0);
                port.setStatus(1);
                String remark = strVal(p.get("remark"));
                String accessUrl = strVal(p.get("accessUrl"));
                if (StringUtils.hasText(accessUrl)) {
                    remark = StringUtils.hasText(remark) ? remark + "; accessUrl=" + accessUrl : "accessUrl=" + accessUrl;
                }
                port.setRemark(remark);

                if (exist != null) {
                    portMapper.updateById(port);
                    stats.incrementUpdated();
                } else {
                    portMapper.insert(port);
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步端口] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ 凭据同步 ============
    private void syncCredentials(boolean override, SyncFromIntelResult.SyncStats stats,
                                 Map<Long, String> intelHostIdToIp, Map<String, Long> ipToOpsHostId,
                                 Map<Long, String> intelSvcIdToName, Map<String, Long> intelSvcNameToOpsSvcId) {
        List<Map<String, Object>> creds = fetchList("/intelligence/machine/internal/credentials");
        stats.setTotal(creds.size());
        log.info("[同步凭据] 从知识引擎获取 {} 条", creds.size());

        for (Map<String, Object> c : creds) {
            try {
                String type = strVal(c.get("credType"));
                String username = strVal(c.get("username"));
                if (type == null) type = "OTHER";

                Long opsHostId = null;
                Long intelHostId = longVal(c.get("hostId"));
                if (intelHostId != null) {
                    String ip = intelHostIdToIp.get(intelHostId);
                    if (ip != null) opsHostId = ipToOpsHostId.get(ip);
                }

                Long opsSvcId = null;
                Long intelSvcId = longVal(c.get("serviceId"));
                if (intelSvcId != null) {
                    String svcName = intelSvcIdToName.get(intelSvcId);
                    if (svcName != null) opsSvcId = intelSvcNameToOpsSvcId.get(svcName);
                }

                Credential exist = null;
                if (opsHostId != null && username != null) {
                    exist = credentialMapper.selectOne(new LambdaQueryWrapper<Credential>()
                            .eq(Credential::getHostId, opsHostId)
                            .eq(Credential::getType, type.toUpperCase())
                            .eq(Credential::getUsername, username));
                }
                if (exist != null && !override) {
                    stats.incrementSkipped();
                    continue;
                }

                Credential cred = exist != null ? exist : new Credential();
                String name = defaultIfBlank(username, type + "-credential");
                cred.setName(name);
                cred.setType(type.toUpperCase());
                cred.setUsername(username);
                String rawPwd = strVal(c.get("passwordEncrypted"));
                if (StringUtils.hasText(rawPwd) && (exist == null || override)) {
                    cred.setPasswordEncrypted(cryptoUtil.encrypt(rawPwd));
                }
                String rawSecret = strVal(c.get("secretKeyEncrypted"));
                if (StringUtils.hasText(rawSecret) && (exist == null || override)) {
                    cred.setSecretKey(cryptoUtil.encrypt(rawSecret));
                }
                if (opsHostId != null) cred.setHostId(opsHostId);
                if (opsSvcId != null) cred.setServiceId(opsSvcId);
                cred.setRemark(strVal(c.get("remark")));

                if (exist != null) {
                    credentialMapper.updateById(cred);
                    stats.incrementUpdated();
                } else {
                    credentialMapper.insert(cred);
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步凭据] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ 域名同步 ============
    private void syncDomains(boolean override, SyncFromIntelResult.SyncStats stats) {
        List<Map<String, Object>> domains = fetchList("/intelligence/machine/internal/domains");
        stats.setTotal(domains.size());
        log.info("[同步域名] 从知识引擎获取 {} 条", domains.size());

        for (Map<String, Object> d : domains) {
            try {
                String domainName = strVal(d.get("domain"));
                if (domainName == null || domainName.isBlank()) {
                    stats.incrementFailed();
                    continue;
                }

                Domain exist = domainMapper.selectOne(
                        new LambdaQueryWrapper<Domain>().eq(Domain::getDomain, domainName));
                if (exist != null && !override) {
                    stats.incrementSkipped();
                    continue;
                }

                Domain dom = exist != null ? exist : new Domain();
                dom.setDomain(domainName);
                dom.setType("SUB_DOMAIN");
                String purpose = buildDomainPurpose(d);
                dom.setPurpose(purpose);
                dom.setStatus(statusToInt(strVal(d.get("status"))));
                dom.setRemark(strVal(d.get("remark")));

                if (exist != null) {
                    domainMapper.updateById(dom);
                    stats.incrementUpdated();
                } else {
                    domainMapper.insert(dom);
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步域名] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ 依赖同步 ============
    private void syncDependencies(boolean override, SyncFromIntelResult.SyncStats stats,
                                  Map<Long, String> intelSvcIdToName, Map<String, Long> intelSvcNameToOpsSvcId) {
        List<Map<String, Object>> deps = fetchList("/intelligence/machine/internal/dependencies");
        stats.setTotal(deps.size());
        log.info("[同步依赖] 从知识引擎获取 {} 条", deps.size());

        for (Map<String, Object> dep : deps) {
            try {
                String fromType = strVal(dep.get("fromType"));
                String toType = strVal(dep.get("toType"));
                if (!"service".equalsIgnoreCase(fromType) || !"service".equalsIgnoreCase(toType)) {
                    stats.incrementSkipped();
                    continue;
                }

                Long intelFromId = longVal(dep.get("fromId"));
                Long intelToId = longVal(dep.get("toId"));
                if (intelFromId == null || intelToId == null) {
                    stats.incrementFailed();
                    continue;
                }

                String fromName = intelSvcIdToName.get(intelFromId);
                String toName = intelSvcIdToName.get(intelToId);
                Long opsFromId = fromName != null ? intelSvcNameToOpsSvcId.get(fromName) : null;
                Long opsToId = toName != null ? intelSvcNameToOpsSvcId.get(toName) : null;

                if (opsFromId == null || opsToId == null || opsFromId.equals(opsToId)) {
                    stats.incrementSkipped();
                    continue;
                }

                Dependency exist = dependencyMapper.selectOne(
                        new LambdaQueryWrapper<Dependency>()
                                .eq(Dependency::getServiceId, opsFromId)
                                .eq(Dependency::getDependsOnServiceId, opsToId));
                if (exist != null && !override) {
                    stats.incrementSkipped();
                    continue;
                }

                Dependency d = exist != null ? exist : new Dependency();
                d.setServiceId(opsFromId);
                d.setDependsOnServiceId(opsToId);
                String depType = strVal(dep.get("depType"));
                d.setDependencyType(StringUtils.hasText(depType) ? depType.toUpperCase() : "REQUIRED");
                d.setDescription(strVal(dep.get("remark")));

                if (exist != null) {
                    dependencyMapper.updateById(d);
                    stats.incrementUpdated();
                } else {
                    dependencyMapper.insert(d);
                    stats.incrementCreated();
                }
            } catch (Exception e) {
                stats.incrementFailed();
                log.warn("[同步依赖] 失败: {}", e.getMessage());
            }
        }
    }

    // ============ HTTP 调用 ============
    private List<Map<String, Object>> fetchList(String path) {
        String url = intelBaseUrl + path;
        try {
            String json = restTemplate.getForObject(url, String.class);
            Result<List<Map<String, Object>>> result = objectMapper.readValue(json,
                    new TypeReference<Result<List<Map<String, Object>>>>() {});
            if (result.getCode() != 200 && result.getCode() != 0) {
                throw new BusinessException("知识引擎接口返回错误: " + result.getMessage());
            }
            return result.getData() != null ? result.getData() : Collections.emptyList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调用知识引擎接口失败 " + path + ": " + e.getMessage());
        }
    }

    // ============ 工具方法 ============
    private String strVal(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer intVal(Object o, Integer def) {
        if (o == null) return def;
        try {
            if (o instanceof Number) return ((Number) o).intValue();
            return Integer.parseInt(o.toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private Long longVal(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return ((Number) o).longValue();
            return Long.parseLong(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultIfBlank(String s, String def) {
        return StringUtils.hasText(s) ? s : def;
    }

    private Integer statusToInt(String status) {
        if (status == null || status.isBlank()) return 1;
        String s = status.toLowerCase();
        if (s.contains("running") || s.contains("active") || s.equals("1")) return 1;
        if (s.contains("stop") || s.contains("down") || s.equals("0")) return 0;
        if (s.contains("maintenance") || s.contains("abnormal") || s.equals("2")) return 2;
        return 1;
    }

    private String buildHostRemark(Map<String, Object> h) {
        List<String> parts = new ArrayList<>();
        if (h.get("osType") != null) parts.add("osType=" + h.get("osType"));
        if (h.get("osVersion") != null) parts.add("osVersion=" + h.get("osVersion"));
        if (h.get("publicIp") != null) parts.add("publicIp=" + h.get("publicIp"));
        if (h.get("memoryGb") != null) parts.add("memoryGb=" + h.get("memoryGb"));
        if (h.get("cpuCores") != null) parts.add("cpuCores=" + h.get("cpuCores"));
        if (h.get("location") != null) parts.add("location=" + h.get("location"));
        if (h.get("environment") != null) parts.add("environment=" + h.get("environment"));
        if (parts.isEmpty()) return strVal(h.get("remark"));
        String extra = String.join(", ", parts);
        String remark = strVal(h.get("remark"));
        return StringUtils.hasText(remark) ? remark + "; " + extra : extra;
    }

    private String buildDomainPurpose(Map<String, Object> d) {
        List<String> parts = new ArrayList<>();
        if (d.get("targetHostId") != null) parts.add("targetHostId=" + d.get("targetHostId"));
        if (d.get("targetPort") != null) parts.add("targetPort=" + d.get("targetPort"));
        if (d.get("targetService") != null) parts.add("targetService=" + d.get("targetService"));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
