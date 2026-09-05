package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marschat.common.exception.BusinessException;
import com.kb.ops.dto.ImportRequest;
import com.kb.ops.dto.ImportResult;
import com.kb.ops.entity.Host;
import com.kb.ops.entity.OpsKnowledge;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.mapper.OpsKnowledgeMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.ImportService;
import com.kb.ops.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运维知识导入服务
 * <p>
 * 接收已解析为 字段名->值 映射的行数据（来源可为 CSV/Excel/JSON），
 * 按类型批量写入。支持根据唯一键判断是否覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final HostMapper hostMapper;
    private final OpsServiceMapper serviceMapper;
    private final OpsKnowledgeMapper knowledgeMapper;
    private final CryptoUtil cryptoUtil;

    @Override
    public ImportResult importData(ImportRequest request) {
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new BusinessException(400, "导入数据为空");
        }
        String type = request.getType() == null ? "" : request.getType().toUpperCase();
        return switch (type) {
            case "HOST" -> importHosts(request.getRows(), request.isOverride());
            case "SERVICE" -> importServices(request.getRows(), request.isOverride());
            case "KNOWLEDGE" -> importKnowledge(request.getRows(), request.isOverride());
            default -> throw new BusinessException(400, "不支持的导入类型: " + type
                    + "（支持 HOST / SERVICE / KNOWLEDGE）");
        };
    }

    // ============ 主机导入 ============
    private ImportResult importHosts(List<Map<String, String>> rows, boolean override) {
        int success = 0, failed = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        int idx = 0;
        for (Map<String, String> row : rows) {
            idx++;
            try {
                String name = val(row, "name", "主机名称");
                String ip = val(row, "ip", "IP");
                if (!StringUtils.hasText(name) || !StringUtils.hasText(ip)) {
                    throw new BusinessException(400, "name 或 ip 为空");
                }
                Host exist = hostMapper.selectOne(new LambdaQueryWrapper<Host>().eq(Host::getIp, ip));
                if (exist != null && !override) {
                    skipped++;
                    continue;
                }
                Host host = exist != null ? exist : new Host();
                host.setName(name);
                host.setIp(ip);
                host.setTailscaleIp(row.get("tailscaleIp"));
                host.setSshPort(parseInt(row.get("sshPort"), 22));
                host.setUsername(row.get("username"));
                if (StringUtils.hasText(row.get("password"))) {
                    host.setPasswordEncrypted(cryptoUtil.encrypt(row.get("password")));
                }
                host.setRole(row.get("role"));
                host.setStatus(parseInt(row.get("status"), 1));
                host.setTags(row.get("tags"));
                host.setRemark(row.get("remark"));
                if (exist != null) {
                    hostMapper.updateById(host);
                } else {
                    hostMapper.insert(host);
                }
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(String.format("第%d行: %s", idx, e.getMessage()));
                log.warn("[导入主机] 第{}行失败: {}", idx, e.getMessage());
            }
        }
        return new ImportResult(rows.size(), success, failed, skipped, errors);
    }

    // ============ 服务导入 ============
    private ImportResult importServices(List<Map<String, String>> rows, boolean override) {
        int success = 0, failed = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        int idx = 0;
        for (Map<String, String> row : rows) {
            idx++;
            try {
                String name = val(row, "name", "服务名称");
                if (!StringUtils.hasText(name)) {
                    throw new BusinessException(400, "name 为空");
                }
                OpsService exist = serviceMapper.selectOne(
                        new LambdaQueryWrapper<OpsService>().eq(OpsService::getName, name));
                if (exist != null && !override) {
                    skipped++;
                    continue;
                }
                OpsService svc = exist != null ? exist : new OpsService();
                svc.setName(name);
                svc.setType(row.get("type"));
                svc.setVersion(row.get("version"));
                svc.setPort(parseInt(row.get("port"), null));
                svc.setHostId(parseLong(row.get("hostId")));
                svc.setDeployPath(row.get("deployPath"));
                svc.setStatus(parseInt(row.get("status"), 1));
                svc.setDependencies(row.get("dependencies"));
                svc.setTags(row.get("tags"));
                svc.setRemark(row.get("remark"));
                if (exist != null) {
                    serviceMapper.updateById(svc);
                } else {
                    serviceMapper.insert(svc);
                }
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(String.format("第%d行: %s", idx, e.getMessage()));
                log.warn("[导入服务] 第{}行失败: {}", idx, e.getMessage());
            }
        }
        return new ImportResult(rows.size(), success, failed, skipped, errors);
    }

    // ============ 运维知识导入 ============
    private ImportResult importKnowledge(List<Map<String, String>> rows, boolean override) {
        int success = 0, failed = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        int idx = 0;
        for (Map<String, String> row : rows) {
            idx++;
            try {
                String title = val(row, "title", "标题");
                if (!StringUtils.hasText(title)) {
                    throw new BusinessException(400, "title 为空");
                }
                OpsKnowledge exist = knowledgeMapper.selectOne(
                        new LambdaQueryWrapper<OpsKnowledge>().eq(OpsKnowledge::getTitle, title));
                if (exist != null && !override) {
                    skipped++;
                    continue;
                }
                OpsKnowledge k = exist != null ? exist : new OpsKnowledge();
                k.setTitle(title);
                k.setCategory(row.get("category"));
                k.setContent(row.get("content"));
                k.setTags(row.get("tags"));
                k.setHostId(parseLong(row.get("hostId")));
                k.setServiceId(parseLong(row.get("serviceId")));
                k.setAuthor(row.get("author"));
                if (exist != null) {
                    knowledgeMapper.updateById(k);
                } else {
                    k.setViewCount(0);
                    knowledgeMapper.insert(k);
                }
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(String.format("第%d行: %s", idx, e.getMessage()));
                log.warn("[导入知识] 第{}行失败: {}", idx, e.getMessage());
            }
        }
        return new ImportResult(rows.size(), success, failed, skipped, errors);
    }

    // ============ 工具方法 ============
    private String val(Map<String, String> row, String key, String label) {
        String v = row.get(key);
        if (v == null) {
            // 容错：尝试忽略大小写
            for (Map.Entry<String, String> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    return e.getValue();
                }
            }
        }
        return v;
    }

    private Integer parseInt(String s, Integer def) {
        if (!StringUtils.hasText(s)) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Long parseLong(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
