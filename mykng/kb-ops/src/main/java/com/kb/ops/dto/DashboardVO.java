package com.kb.ops.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 运维看板数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    /** 主机统计：运行中/停机/维护中 数量 */
    private Map<String, Long> hostStats;

    /** 服务统计：运行中/已停止/异常 数量 */
    private Map<String, Long> serviceStats;

    /** 服务类型分布 */
    private Map<String, Long> serviceTypeDistribution;

    /** 最近部署记录数 */
    private long recentDeployCount;

    /** 未处理矛盾数 */
    private long unresolvedConflictCount;

    /** 最近 7 天部署趋势（日期 -> 数量） */
    private List<Map<String, Object>> deployTrend;

    /** 最近部署记录 */
    private List<DeploymentRow> recentDeploys;

    /** 最近矛盾记录 */
    private List<ConflictRow> recentConflicts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeploymentRow {
        private String serviceName;
        private String version;
        private String operator;
        private String deployTime;
        private Integer result;
        private Integer rollback;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictRow {
        private String ruleCode;
        private String ruleName;
        private Integer severity;
        private String targetName;
        private String detail;
        private String detectedAt;
    }
}
