package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.ops.dto.DashboardVO;
import com.kb.ops.entity.*;
import com.kb.ops.mapper.*;
import com.kb.ops.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final HostMapper hostMapper;
    private final OpsServiceMapper serviceMapper;
    private final DeploymentRecordMapper recordMapper;
    private final OpsConflictMapper conflictMapper;
    private final OpsSnapshotMapper snapshotMapper;

    @Override
    public DashboardVO getDashboard() {
        List<Host> hosts = hostMapper.selectList(null);
        List<OpsService> services = serviceMapper.selectList(null);
        List<DeploymentRecord> recentRecords = recordMapper.selectList(
                new LambdaQueryWrapper<DeploymentRecord>()
                        .orderByDesc(DeploymentRecord::getDeployTime)
                        .last("LIMIT 10"));
        List<OpsConflict> recentConflicts = conflictMapper.selectList(
                new LambdaQueryWrapper<OpsConflict>()
                        .orderByDesc(OpsConflict::getDetectedAt)
                        .last("LIMIT 10"));
        Long unresolved = conflictMapper.selectCount(
                new LambdaQueryWrapper<OpsConflict>().eq(OpsConflict::getStatus, 0));

        // 主机统计
        Map<String, Long> hostStats = new LinkedHashMap<>();
        hostStats.put("running", count(hosts, h -> h.getStatus() != null && h.getStatus() == 1));
        hostStats.put("stopped", count(hosts, h -> h.getStatus() != null && h.getStatus() == 0));
        hostStats.put("maintenance", count(hosts, h -> h.getStatus() != null && h.getStatus() == 2));
        hostStats.put("total", (long) hosts.size());

        // 服务统计
        Map<String, Long> serviceStats = new LinkedHashMap<>();
        serviceStats.put("running", count(services, s -> s.getStatus() != null && s.getStatus() == 1));
        serviceStats.put("stopped", count(services, s -> s.getStatus() != null && s.getStatus() == 0));
        serviceStats.put("abnormal", count(services, s -> s.getStatus() != null && s.getStatus() == 2));
        serviceStats.put("total", (long) services.size());

        // 服务类型分布
        Map<String, Long> typeDist = services.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getType() == null ? "unknown" : s.getType(),
                        Collectors.counting()));

        // 最近 7 天部署趋势（直接查询7天数据，而非复用LIMIT 10结果）
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<DeploymentRecord> trendRecords = recordMapper.selectList(
                new LambdaQueryWrapper<DeploymentRecord>()
                        .ge(DeploymentRecord::getDeployTime, sevenDaysAgo)
                        .orderByDesc(DeploymentRecord::getDeployTime));
        Map<String, Long> trendMap = trendRecords.stream()
                .filter(r -> r.getDeployTime() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getDeployTime().toLocalDate().format(fmt),
                        Collectors.counting()));
        List<Map<String, Object>> deployTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(fmt);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day);
            item.put("count", trendMap.getOrDefault(day, 0L));
            deployTrend.add(item);
        }

        // 组装最近部署记录
        List<DashboardVO.DeploymentRow> deployRows = recentRecords.stream()
                .map(r -> new DashboardVO.DeploymentRow(
                        r.getServiceName(),
                        r.getVersion(),
                        r.getOperator(),
                        r.getDeployTime() == null ? null : r.getDeployTime().format(fmt),
                        r.getResult(),
                        r.getRollback()))
                .collect(Collectors.toList());

        List<DashboardVO.ConflictRow> conflictRows = recentConflicts.stream()
                .map(c -> new DashboardVO.ConflictRow(
                        c.getRuleCode(),
                        c.getRuleName(),
                        c.getSeverity(),
                        c.getTargetName(),
                        c.getDetail(),
                        c.getDetectedAt() == null ? null : c.getDetectedAt().format(fmt)))
                .collect(Collectors.toList());

        DashboardVO vo = new DashboardVO();
        vo.setHostStats(hostStats);
        vo.setServiceStats(serviceStats);
        vo.setServiceTypeDistribution(typeDist);
        vo.setRecentDeployCount(recordMapper.selectCount(null));
        vo.setUnresolvedConflictCount(unresolved == null ? 0 : unresolved);
        vo.setDeployTrend(deployTrend);
        vo.setRecentDeploys(deployRows);
        vo.setRecentConflicts(conflictRows);
        return vo;
    }

    @Override
    public void refreshSnapshot() {
        LocalDate today = LocalDate.now();
        List<Host> hosts = hostMapper.selectList(null);
        List<OpsService> services = serviceMapper.selectList(null);

        upsertSnapshot(today, "host_total", hosts.size());
        upsertSnapshot(today, "host_running",
                (int) count(hosts, h -> h.getStatus() != null && h.getStatus() == 1));
        upsertSnapshot(today, "service_total", services.size());
        upsertSnapshot(today, "service_running",
                (int) count(services, s -> s.getStatus() != null && s.getStatus() == 1));

        Long unresolved = conflictMapper.selectCount(
                new LambdaQueryWrapper<OpsConflict>().eq(OpsConflict::getStatus, 0));
        upsertSnapshot(today, "conflict_unresolved", unresolved == null ? 0 : unresolved.intValue());
        log.info("[看板快照] 已刷新 {} 指标", 5);
    }

    /**
     * 每天 03:00 刷新快照（与架构方案 P1 一致性任务对齐）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledSnapshot() {
        try {
            refreshSnapshot();
        } catch (Exception e) {
            log.error("[看板快照] 定时刷新失败", e);
        }
    }

    private void upsertSnapshot(LocalDate date, String key, int value) {
        OpsSnapshot exist = snapshotMapper.selectOne(
                new LambdaQueryWrapper<OpsSnapshot>()
                        .eq(OpsSnapshot::getSnapshotDate, date)
                        .eq(OpsSnapshot::getMetricKey, key));
        if (exist == null) {
            OpsSnapshot snapshot = new OpsSnapshot();
            snapshot.setSnapshotDate(date);
            snapshot.setMetricKey(key);
            snapshot.setMetricValue((long) value);
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshotMapper.insert(snapshot);
        } else {
            exist.setMetricValue((long) value);
            snapshotMapper.updateById(exist);
        }
    }

    private <T> long count(List<T> list, java.util.function.Predicate<T> p) {
        return list.stream().filter(p).count();
    }
}
