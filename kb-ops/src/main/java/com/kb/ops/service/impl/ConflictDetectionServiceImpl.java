package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.entity.Host;
import com.kb.ops.entity.OpsConflict;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.mapper.OpsConflictMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 矛盾检测服务实现
 * <p>
 * 每次检测先清空旧的「未处理」记录，再重新写入本次检测到的矛盾，
 * 已忽略/已解决的记录保留不动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionServiceImpl implements ConflictDetectionService {

    private final HostMapper hostMapper;
    private final OpsServiceMapper serviceMapper;
    private final OpsConflictMapper conflictMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int detect() {
        // 清空旧的未处理记录
        conflictMapper.delete(new LambdaQueryWrapper<OpsConflict>()
                .eq(OpsConflict::getStatus, 0));

        List<Host> hosts = hostMapper.selectList(null);
        List<OpsService> services = serviceMapper.selectList(null);
        List<OpsConflict> conflicts = new ArrayList<>();

        conflicts.addAll(detectVersionMismatch(services));
        conflicts.addAll(detectPortConflict(services));
        conflicts.addAll(detectHostDownServiceRunning(hosts, services));
        conflicts.addAll(detectDuplicateHostIp(hosts));
        conflicts.addAll(detectMissingDependency(services));
        conflicts.addAll(detectDuplicateServiceName(services));

        // 批量写入
        for (OpsConflict c : conflicts) {
            conflictMapper.insert(c);
        }
        log.info("[矛盾检测] 完成，共检测到 {} 条矛盾", conflicts.size());
        return conflicts.size();
    }

    @Override
    public PageResult<OpsConflict> list(String ruleCode, Integer status, int page, int size) {
        LambdaQueryWrapper<OpsConflict> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(ruleCode)) {
            wrapper.eq(OpsConflict::getRuleCode, ruleCode);
        }
        if (status != null) {
            wrapper.eq(OpsConflict::getStatus, status);
        }
        wrapper.orderByDesc(OpsConflict::getSeverity)
                .orderByDesc(OpsConflict::getDetectedAt);
        Page<OpsConflict> p = conflictMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public void resolve(Long id) {
        OpsConflict c = conflictMapper.selectById(id);
        if (c == null) {
            throw new NotFoundException("矛盾记录", id);
        }
        c.setStatus(2);
        conflictMapper.updateById(c);
    }

    // ============ 规则实现 ============

    /**
     * 规则1: 同一服务（同名）在不同主机上版本不一致
     */
    private List<OpsConflict> detectVersionMismatch(List<OpsService> services) {
        // 按服务名称分组
        Map<String, List<OpsService>> byName = services.stream()
                .filter(s -> StringUtils.hasText(s.getName()) && StringUtils.hasText(s.getVersion()))
                .collect(Collectors.groupingBy(OpsService::getName));

        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<OpsService>> entry : byName.entrySet()) {
            Set<String> versions = entry.getValue().stream()
                    .map(OpsService::getVersion)
                    .collect(Collectors.toSet());
            if (versions.size() > 1) {
                String detail = String.format("服务[%s]在 %d 个实例中存在版本不一致: %s",
                        entry.getKey(), entry.getValue().size(), versions);
                for (OpsService s : entry.getValue()) {
                    result.add(build("VERSION_MISMATCH", "版本不一致", 3, "SERVICE",
                            s.getId(), s.getName(), detail, now));
                }
            }
        }
        return result;
    }

    /**
     * 规则2: 同一主机同端口被多个服务占用
     */
    private List<OpsConflict> detectPortConflict(List<OpsService> services) {
        Map<String, List<OpsService>> byHostPort = services.stream()
                .filter(s -> s.getHostId() != null && s.getPort() != null)
                .collect(Collectors.groupingBy(s -> s.getHostId() + ":" + s.getPort()));

        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<OpsService>> entry : byHostPort.entrySet()) {
            if (entry.getValue().size() > 1) {
                String detail = String.format("主机[%s]端口[%s]被 %d 个服务占用",
                        entry.getValue().get(0).getHostId(),
                        entry.getKey().split(":").length > 1 ? entry.getKey().split(":")[1] : entry.getKey(),
                        entry.getValue().size());
                for (OpsService s : entry.getValue()) {
                    result.add(build("PORT_CONFLICT", "端口冲突", 3, "SERVICE",
                            s.getId(), s.getName(), detail, now));
                }
            }
        }
        return result;
    }

    /**
     * 规则3: 主机停机/维护中，但其上的服务仍标记为运行中
     */
    private List<OpsConflict> detectHostDownServiceRunning(List<Host> hosts, List<OpsService> services) {
        Map<Long, Host> hostMap = hosts.stream()
                .collect(Collectors.toMap(Host::getId, h -> h, (a, b) -> a));
        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (OpsService s : services) {
            if (s.getHostId() == null || s.getStatus() == null || s.getStatus() != 1) {
                continue;
            }
            Host h = hostMap.get(s.getHostId());
            if (h != null && h.getStatus() != null && h.getStatus() != 1) {
                String hostStatus = h.getStatus() == 0 ? "停机" : "维护中";
                String detail = String.format("主机[%s]当前%s，但服务[%s]仍标记为运行中",
                        h.getName(), hostStatus, s.getName());
                result.add(build("HOST_DOWN_SERVICE_RUNNING", "主机异常服务运行中", 2, "SERVICE",
                        s.getId(), s.getName(), detail, now));
            }
        }
        return result;
    }

    /**
     * 规则4: 重复的主机内网IP
     */
    private List<OpsConflict> detectDuplicateHostIp(List<Host> hosts) {
        Map<String, List<Host>> byIp = hosts.stream()
                .filter(h -> StringUtils.hasText(h.getIp()))
                .collect(Collectors.groupingBy(Host::getIp));
        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<Host>> entry : byIp.entrySet()) {
            if (entry.getValue().size() > 1) {
                String detail = String.format("IP[%s]被 %d 台主机占用", entry.getKey(), entry.getValue().size());
                for (Host h : entry.getValue()) {
                    result.add(build("DUPLICATE_HOST_IP", "主机IP重复", 2, "HOST",
                            h.getId(), h.getName(), detail, now));
                }
            }
        }
        return result;
    }

    /**
     * 规则5: 服务依赖了不存在的服务
     */
    private List<OpsConflict> detectMissingDependency(List<OpsService> services) {
        Set<String> serviceNames = services.stream()
                .map(OpsService::getName)
                .collect(Collectors.toSet());
        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (OpsService s : services) {
            if (!StringUtils.hasText(s.getDependencies())) {
                continue;
            }
            String[] deps = s.getDependencies().split(",");
            for (String dep : deps) {
                String name = dep.trim();
                if (!name.isEmpty() && !serviceNames.contains(name)) {
                    String detail = String.format("服务[%s]依赖的服务[%s]不存在", s.getName(), name);
                    result.add(build("MISSING_DEPENDENCY", "依赖缺失", 2, "SERVICE",
                            s.getId(), s.getName(), detail, now));
                }
            }
        }
        return result;
    }

    /**
     * 规则6: 同名服务（不同实例）部署在不同主机但端口/路径配置可能冲突的辅助检测
     * ——这里检测同名服务部署在同一台主机（同主机同名多实例）
     */
    private List<OpsConflict> detectDuplicateServiceName(List<OpsService> services) {
        Map<String, List<OpsService>> byHostAndName = services.stream()
                .filter(s -> s.getHostId() != null && StringUtils.hasText(s.getName()))
                .collect(Collectors.groupingBy(s -> s.getHostId() + "::" + s.getName()));
        List<OpsConflict> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<OpsService>> entry : byHostAndName.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("::");
                String detail = String.format("主机[%s]上部署了 %d 个同名服务[%s]",
                        parts[0], entry.getValue().size(), parts.length > 1 ? parts[1] : "");
                for (OpsService s : entry.getValue()) {
                    result.add(build("DUPLICATE_SERVICE_NAME", "同主机同名服务", 1, "SERVICE",
                            s.getId(), s.getName(), detail, now));
                }
            }
        }
        return result;
    }

    private OpsConflict build(String ruleCode, String ruleName, int severity,
                              String targetType, Long targetId, String targetName,
                              String detail, LocalDateTime detectedAt) {
        OpsConflict c = new OpsConflict();
        c.setRuleCode(ruleCode);
        c.setRuleName(ruleName);
        c.setSeverity(severity);
        c.setTargetType(targetType);
        c.setTargetId(targetId);
        c.setTargetName(targetName);
        c.setDetail(detail);
        c.setStatus(0);
        c.setDetectedAt(detectedAt);
        c.setCreatedAt(detectedAt);
        return c;
    }
}
