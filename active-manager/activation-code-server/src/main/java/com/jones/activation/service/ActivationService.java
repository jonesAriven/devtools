package com.jones.activation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jones.activation.dto.GenerateRequest;
import com.jones.activation.dto.GenerateResponse;
import com.jones.activation.dto.VerifyRequest;
import com.jones.activation.dto.VerifyResponse;
import com.jones.activation.entity.ActivationLog;
import com.jones.activation.entity.ActivationRecord;
import com.jones.activation.entity.SysConfig;
import com.jones.activation.mapper.ActivationLogMapper;
import com.jones.activation.mapper.ActivationRecordMapper;
import com.jones.activation.mapper.SysConfigMapper;
import com.jones.activation.util.CryptoUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final CryptoUtil cryptoUtil;
    private final ActivationRecordMapper activationRecordMapper;
    private final ActivationLogMapper activationLogMapper;
    private final SysConfigMapper sysConfigMapper;

    public ActivationService(CryptoUtil cryptoUtil, ActivationRecordMapper activationRecordMapper,
                             ActivationLogMapper activationLogMapper, SysConfigMapper sysConfigMapper) {
        this.cryptoUtil = cryptoUtil;
        this.activationRecordMapper = activationRecordMapper;
        this.activationLogMapper = activationLogMapper;
        this.sysConfigMapper = sysConfigMapper;
    }

    public GenerateResponse generateActivationCode(GenerateRequest request) {
        String serialNumber = request.getSerialNumber();
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            log.warn("生成激活码失败: 序列号为空");
            return GenerateResponse.builder()
                    .success(false)
                    .message("序列号不能为空")
                    .build();
        }

        String deviceId = request.getDeviceId();
        String initialSerial;
        String machineCode;

        // 先解析序列号，获取其中嵌入的版本号
        String clientVersionFromSerial = null;
        CryptoUtil.SerialNumberParseResult parseResult = CryptoUtil.decryptSerialNumber(serialNumber);
        if (parseResult.isSuccess()) {
            initialSerial = parseResult.getInitialSerial();
            String parsedDeviceId = parseResult.getDeviceId();
            machineCode = parseResult.getMachineCode();
            clientVersionFromSerial = parseResult.getVersion();

            serialNumber = initialSerial + "-" + machineCode;
            if (deviceId == null || deviceId.trim().isEmpty()) {
                deviceId = parsedDeviceId;
            }

            log.info("从加密序列号解析: 初始序列号={}, 设备ID={}, 机器码={}", initialSerial, parsedDeviceId, machineCode);
        } else {
            String[] parsed = parseSerialNumber(serialNumber);
            initialSerial = parsed[0];
            machineCode = parsed[1];
        }

        // ========== 版本校验 ==========
        // 优先从 HTTP 请求取 clientVersion，如果没有则从序列号解析
        String clientVersion = request.getClientVersion();
        if (clientVersion == null || clientVersion.trim().isEmpty()) {
            clientVersion = clientVersionFromSerial;
        }
        GenerateResponse versionCheck = checkVersionRestriction(clientVersion);
        if (versionCheck != null) {
            return versionCheck;
        }

        int expireMinutes = request.getExpireMinutes() != null ? request.getExpireMinutes() : 525600;
        if (expireMinutes <= 0) {
            log.warn("生成激活码失败: 过期分钟数无效, expireMinutes={}", expireMinutes);
            return GenerateResponse.builder()
                    .success(false)
                    .message("过期分钟数必须大于0")
                    .build();
        }

        long expireTimestamp = System.currentTimeMillis() + (long) expireMinutes * 60 * 1000;

        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivationRecord::getSerialNumber, serialNumber);
        ActivationRecord existingRecord = activationRecordMapper.selectOne(queryWrapper);

        if (existingRecord != null) {
            if (existingRecord.getExpireTime() > System.currentTimeMillis()) {
                log.info("序列号已存在且未过期, 序列号: {}, 设备ID: {}, 剩余有效期: {}分钟",
                        serialNumber, existingRecord.getDeviceId(),
                        (existingRecord.getExpireTime() - System.currentTimeMillis()) / (60 * 1000));

                saveLog(null, serialNumber, deviceId, "GENERATE_DUPLICATE",
                        "序列号已存在且未过期，拒绝重复生成", getClientIp());

                return GenerateResponse.builder()
                        .success(false)
                        .message("该序列号已存在且未过期，无需重新激活")
                        .activationCode(existingRecord.getActivationCode())
                        .expireTime(existingRecord.getExpireTime())
                        .serialNumber(serialNumber)
                        .deviceId(existingRecord.getDeviceId())
                        .build();
            }
        }

        String activationCode = cryptoUtil.generateActivationCode(serialNumber, deviceId, expireTimestamp);

        Long recordId = null;
        if (existingRecord != null) {
            existingRecord.setActivationCode(activationCode);
            existingRecord.setDeviceId(deviceId);
            existingRecord.setExpireTime(expireTimestamp);
            existingRecord.setExpireMinutes(expireMinutes);
            existingRecord.setInitialSerial(initialSerial);
            existingRecord.setMachineCode(machineCode);
            existingRecord.setActivatedTime(null);
            existingRecord.setUpdateTime(LocalDateTime.now());
            activationRecordMapper.updateById(existingRecord);
            recordId = existingRecord.getId();
            log.info("更新激活码记录, 序列号: {}, 设备ID: {}, 新过期时间: {}", serialNumber, deviceId, expireTimestamp);
        } else {
            ActivationRecord record = new ActivationRecord();
            record.setSerialNumber(serialNumber);
            record.setDeviceId(deviceId);
            record.setActivationCode(activationCode);
            record.setExpireTime(expireTimestamp);
            record.setExpireMinutes(expireMinutes);
            record.setInitialSerial(initialSerial);
            record.setMachineCode(machineCode);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            activationRecordMapper.insert(record);
            recordId = record.getId();
            log.info("新增激活码记录, 序列号: {}, 设备ID: {}, 过期时间: {}", serialNumber, deviceId, expireTimestamp);
        }

        saveLog(recordId, serialNumber, deviceId, "GENERATE",
                "生成激活码, 有效期" + expireMinutes + "分钟", getClientIp());

        return GenerateResponse.builder()
                .success(true)
                .message("激活码生成成功")
                .activationCode(activationCode)
                .expireTime(expireTimestamp)
                .serialNumber(serialNumber)
                .deviceId(deviceId)
                .initialSerial(initialSerial)
                .machineCode(machineCode)
                .build();
    }

    public VerifyResponse verifyActivationCode(VerifyRequest request) {
        String activationCode = request.getActivationCode();
        if (activationCode == null || activationCode.trim().isEmpty()) {
            log.warn("验证激活码失败: 激活码为空");
            return VerifyResponse.builder()
                    .success(false)
                    .message("激活码不能为空")
                    .build();
        }

        String requestDeviceId = request.getDeviceId();
        CryptoUtil.ActivationCodeParseResult result = cryptoUtil.parseAndVerify(activationCode, requestDeviceId);

        if (!result.isValid()) {
            String eventType = result.isDeviceMismatch() ? "DEVICE_MISMATCH" : "VERIFY_FAIL";
            String eventMsg = result.isDeviceMismatch()
                    ? "设备不匹配, 绑定设备:" + result.getDeviceId() + ", 请求设备:" + requestDeviceId
                    : result.getMessage();
            saveLogBySerialNumber(result.getSerialNumber(), requestDeviceId, eventType, eventMsg);

            if (result.isDeviceMismatch()) {
                return VerifyResponse.builder()
                        .success(false)
                        .message(result.getMessage())
                        .serialNumber(result.getSerialNumber())
                        .deviceId(result.getDeviceId())
                        .expireTime(result.getExpireTimestamp())
                        .deviceMismatch(true)
                        .build();
            }
            return VerifyResponse.builder()
                    .success(false)
                    .message(result.getMessage())
                    .build();
        }

        boolean expired = result.getExpireTimestamp() < System.currentTimeMillis();
        if (expired) {
            log.info("激活码已过期, 序列号: {}, 设备ID: {}, 过期时间: {}",
                    result.getSerialNumber(), result.getDeviceId(), result.getExpireTimestamp());

            saveLogBySerialNumber(result.getSerialNumber(), requestDeviceId, "EXPIRED",
                    "激活码已过期, 过期时间:" + new java.sql.Timestamp(result.getExpireTimestamp()));

            return VerifyResponse.builder()
                    .success(false)
                    .message("激活码已过期")
                    .serialNumber(result.getSerialNumber())
                    .deviceId(result.getDeviceId())
                    .expireTime(result.getExpireTimestamp())
                    .expired(true)
                    .build();
        }

        updateActivatedTime(result.getSerialNumber());

        saveLogBySerialNumber(result.getSerialNumber(), requestDeviceId, "VERIFY_SUCCESS",
                "激活码验证成功", result.getExpireTimestamp());

        return VerifyResponse.builder()
                .success(true)
                .message("激活码验证成功")
                .serialNumber(result.getSerialNumber())
                .deviceId(result.getDeviceId())
                .expireTime(result.getExpireTimestamp())
                .expired(false)
                .deviceMismatch(false)
                .build();
    }

    public Map<String, Object> queryRecords(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ActivationRecord::getCreateTime);

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(w -> w
                    .like(ActivationRecord::getSerialNumber, keyword)
                    .or()
                    .like(ActivationRecord::getDeviceId, keyword)
                    .or()
                    .like(ActivationRecord::getInitialSerial, keyword)
                    .or()
                    .like(ActivationRecord::getMachineCode, keyword)
                    .or()
                    .like(ActivationRecord::getDeviceAlias, keyword)
            );
        }

        if ("active".equals(status)) {
            queryWrapper.gt(ActivationRecord::getExpireTime, System.currentTimeMillis());
        } else if ("expired".equals(status)) {
            queryWrapper.le(ActivationRecord::getExpireTime, System.currentTimeMillis());
        }

        Page<ActivationRecord> pageResult = activationRecordMapper.selectPage(new Page<>(page, size), queryWrapper);
        long now = System.currentTimeMillis();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("data", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("currentTime", now);
        return result;
    }

    public Map<String, Object> queryLogs(Long recordId, String serialNumber, String eventType,
                                          String deviceId, String startDate, String endDate,
                                          int page, int size) {
        LambdaQueryWrapper<ActivationLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ActivationLog::getCreateTime);

        if (recordId != null) {
            queryWrapper.eq(ActivationLog::getRecordId, recordId);
        }
        if (serialNumber != null && !serialNumber.trim().isEmpty()) {
            queryWrapper.like(ActivationLog::getSerialNumber, serialNumber);
        }
        if (eventType != null && !eventType.trim().isEmpty()) {
            queryWrapper.eq(ActivationLog::getEventType, eventType);
        }
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            queryWrapper.like(ActivationLog::getDeviceId, deviceId);
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
                queryWrapper.ge(ActivationLog::getCreateTime, start);
            } catch (Exception ignored) {}
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
                queryWrapper.le(ActivationLog::getCreateTime, end);
            } catch (Exception ignored) {}
        }

        Page<ActivationLog> pageResult = activationLogMapper.selectPage(new Page<>(page, size), queryWrapper);

        // 关联查询设备别名：通过 serial_number 关联 activation_record 获取 device_alias
        for (ActivationLog logEntry : pageResult.getRecords()) {
            if (logEntry.getSerialNumber() != null) {
                LambdaQueryWrapper<ActivationRecord> recordQuery = new LambdaQueryWrapper<>();
                recordQuery.eq(ActivationRecord::getSerialNumber, logEntry.getSerialNumber());
                recordQuery.select(ActivationRecord::getDeviceAlias);
                ActivationRecord record = activationRecordMapper.selectOne(recordQuery);
                if (record != null) {
                    logEntry.setDeviceAlias(record.getDeviceAlias());
                }
            }
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("data", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        return result;
    }

    public boolean deleteRecord(Long id) {
        return activationRecordMapper.deleteById(id) > 0;
    }
    public java.util.Map<String, Object> batchDeleteRecords(java.util.List<Long> ids) {
        int deletedCount = 0;
        for (Long id : ids) {
            int result = activationRecordMapper.deleteById(id);
            if (result > 0) deletedCount++;
        }
        boolean allSuccess = deletedCount == ids.size();
        return java.util.Map.of(
            "success", true,
            "deletedCount", deletedCount,
            "totalCount", ids.size(),
            "message", allSuccess ? "成功删除" + deletedCount + "条记录" : "成功删除" + deletedCount + "/" + ids.size() + "条（部分记录不存在）"
        );
    }

    public java.util.Map<String, Object> updateDeviceAlias(Long id, String alias) {
        ActivationRecord record = activationRecordMapper.selectById(id);
        if (record == null) {
            return Map.of("success", false, "message", "记录不存在");
        }

        // 唯一性校验：别名不能与其他记录重复（空值允许重复）
        if (alias != null && !alias.trim().isEmpty()) {
            alias = alias.trim();
            LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ActivationRecord::getDeviceAlias, alias);
            queryWrapper.ne(ActivationRecord::getId, id);
            ActivationRecord existing = activationRecordMapper.selectOne(queryWrapper);
            if (existing != null) {
                return Map.of("success", false, "message", "设备别名已存在，请使用其他名称");
            }
            record.setDeviceAlias(alias);
        } else {
            record.setDeviceAlias(null);
        }

        record.setUpdateTime(LocalDateTime.now());
        activationRecordMapper.updateById(record);

        log.info("更新设备别名, id: {}, alias: {}", id, alias);
        return Map.of("success", true, "message", "设备别名更新成功");
    }

    public java.util.Map<String, Object> parseActivationCode(String activationCode) {
        CryptoUtil.ActivationCodeParseResult result = cryptoUtil.parseAndVerify(activationCode, null);
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (result.isValid()) {
            map.put("success", true);
            map.put("serialNumber", result.getSerialNumber());
            map.put("deviceId", result.getDeviceId());
            map.put("expireTime", result.getExpireTimestamp());
            map.put("expireDate", new java.sql.Timestamp(result.getExpireTimestamp()).toString());
            map.put("expired", result.getExpireTimestamp() < System.currentTimeMillis());

            String[] parsed = parseSerialNumber(result.getSerialNumber());
            map.put("initialSerial", parsed[0]);
            map.put("machineCode", parsed[1]);
        } else {
            map.put("success", false);
            map.put("message", result.getMessage());
            if (result.getSerialNumber() != null) {
                map.put("serialNumber", result.getSerialNumber());
                map.put("deviceId", result.getDeviceId());
                map.put("expireTime", result.getExpireTimestamp());
            }
        }
        return map;
    }

    public java.util.Map<String, Object> parseSerialNumberInfo(String serialNumber) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("serialNumber", serialNumber);

        // 先尝试解密序列号（兼容加密格式：Base64 XOR编码）
        String initialSerial;
        String machineCode;
        String parsedDeviceId = null;

        CryptoUtil.SerialNumberParseResult decryptResult = CryptoUtil.decryptSerialNumber(serialNumber);
        if (decryptResult.isSuccess()) {
            // 解密成功：返回结构化的初始序列号、设备ID、机器码
            initialSerial = decryptResult.getInitialSerial();
            machineCode = decryptResult.getMachineCode();
            parsedDeviceId = decryptResult.getDeviceId();
            log.info("解析序列号(解密成功): 初始序列号={}, 机器码={}, 设备ID={}", initialSerial, machineCode, parsedDeviceId);
        } else {
            // 解密失败：降级为明文格式（按最后一个 '-' 分割，如 SOFT001-AA-BB-CC-DD-EE-FF）
            String[] parsed = parseSerialNumber(serialNumber);
            initialSerial = parsed[0];
            machineCode = parsed[1];
            log.info("解析序列号(明文模式): 初始序列号={}, 机器码={}", initialSerial, machineCode);
        }

        map.put("initialSerial", initialSerial);
        map.put("machineCode", machineCode);

        // 查询数据库记录时使用解密后重组的 serialNumber（与 generateActivationCode 保持一致）
        String dbSerialNumber = serialNumber;
        if (decryptResult.isSuccess()) {
            dbSerialNumber = initialSerial + "-" + machineCode;
        }

        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivationRecord::getSerialNumber, dbSerialNumber);
        ActivationRecord record = activationRecordMapper.selectOne(queryWrapper);
        if (record != null) {
            map.put("recordId", record.getId());
            map.put("deviceId", record.getDeviceId());
            map.put("expireTime", record.getExpireTime());
            map.put("expireMinutes", record.getExpireMinutes());
            map.put("activatedTime", record.getActivatedTime() != null ? record.getActivatedTime().toString() : null);
            map.put("createTime", record.getCreateTime() != null ? record.getCreateTime().toString() : null);
            map.put("expired", record.getExpireTime() < System.currentTimeMillis());
        } else if (parsedDeviceId != null && !parsedDeviceId.isEmpty()) {
            // 数据库无记录时，仍返回从序列号中解析出的 deviceId
            map.put("deviceId", parsedDeviceId);
        }
        return map;
    }

    private void updateActivatedTime(String serialNumber) {
        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivationRecord::getSerialNumber, serialNumber);
        ActivationRecord record = activationRecordMapper.selectOne(queryWrapper);
        if (record != null && record.getActivatedTime() == null) {
            record.setActivatedTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            activationRecordMapper.updateById(record);
        }
    }

    private String[] parseSerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isEmpty()) {
            return new String[]{"", ""};
        }
        int lastDash = serialNumber.lastIndexOf('-');
        if (lastDash > 0) {
            return new String[]{
                    serialNumber.substring(0, lastDash),
                    serialNumber.substring(lastDash + 1)
            };
        }
        return new String[]{serialNumber, ""};
    }

    private void saveLog(Long recordId, String serialNumber, String deviceId,
                         String eventType, String eventMessage, String clientIp) {
        try {
            ActivationLog logEntry = new ActivationLog();
            logEntry.setRecordId(recordId);
            logEntry.setSerialNumber(serialNumber);
            logEntry.setDeviceId(deviceId);
            logEntry.setEventType(eventType);
            logEntry.setEventMessage(eventMessage);
            logEntry.setClientIp(clientIp);
            logEntry.setCreateTime(LocalDateTime.now());

            activationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("保存激活日志失败", e);
        }
    }

    private void saveLogBySerialNumber(String serialNumber, String deviceId,
                                       String eventType, String eventMessage, long expireTimestamp) {
        Long recordId = null;
        if (serialNumber != null) {
            LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ActivationRecord::getSerialNumber, serialNumber);
            ActivationRecord record = activationRecordMapper.selectOne(queryWrapper);
            if (record != null) {
                recordId = record.getId();
            }
        }
        saveLog(recordId, serialNumber, deviceId, eventType, eventMessage, getClientIp());
    }

    private void saveLogBySerialNumber(String serialNumber, String deviceId,
                                       String eventType, String eventMessage) {
        saveLogBySerialNumber(serialNumber, deviceId, eventType, eventMessage, 0);
    }

    // ==================== 版本校验逻辑 ====================

    /**
     * 版本限制校验
     * @param clientVersion 客户端传来的版本号（可能为null）
     * @return 如果校验不通过返回错误Response，如果通过返回null继续执行
     */
    private GenerateResponse checkVersionRestriction(String clientVersion) {
        String enabled = getConfigValue("version-check.enabled", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            return null; // 未启用版本校验，直接放行
        }

        String mode = getConfigValue("version-check.mode", "none");

        switch (mode) {
            case "required":
                // 必须传版本号，没传就拒绝
                if (clientVersion == null || clientVersion.trim().isEmpty()) {
                    log.warn("版本校验失败: 客户端未传版本号 (mode=required)");
                    return buildVersionRejectResponse(
                        "客户端版本信息缺失，请下载最新版本工具",
                        null, clientVersion);
                }
                log.info("版本校验通过: version={}, mode=required", clientVersion);
                return null;

            case "minimum":
                // 最低版本号要求
                String minVersion = getConfigValue("version-check.min-version", "0.0.0");
                if (clientVersion == null || clientVersion.trim().isEmpty()) {
                    // 没传版本号也视为版本过低
                    log.warn("版本校验失败: 客户端未传版本号 (mode=minimum, min={})", minVersion);
                    return buildVersionRejectResponse(
                        "无法识别客户端版本，请下载最新版本工具",
                        minVersion, null);
                }
                if (compareVersions(clientVersion, minVersion) < 0) {
                    log.warn("版本校验失败: 客户端版本 {} 低于最低要求 {}", clientVersion, minVersion);
                    return buildVersionRejectResponse(
                        "当前版本过低，请下载最新版本工具后重试",
                        minVersion, clientVersion);
                }
                log.info("版本校验通过: version={}, min={}, mode=minimum", clientVersion, minVersion);
                return null;

            case "none":
            default:
                return null;
        }
    }

    /**
     * 构建版本校验拒绝的响应
     */
    private GenerateResponse buildVersionRejectResponse(String message, String minVersion, String clientVersion) {
        String downloadUrl = getConfigValue("version-check.download-url", "");
        saveLog(null, "", "", "VERSION_REJECTED",
                "版本校验拒绝: " + message + ", clientVersion=" + (clientVersion != null ? clientVersion : "空") + ", minVersion=" + (minVersion != null ? minVersion : "未设置"),
                getClientIp());
        return GenerateResponse.builder()
                .success(false)
                .message(message)
                .downloadUrl(downloadUrl)
                .clientVersion(clientVersion)
                .build();
    }

    /**
     * 比较两个版本号
     * @return v1 > v2 返回正数, v1 < v2 返回负数, 相等返回0
     * 支持格式: "1.2.3", "202607152217" (纯数字按字符串比较), "V202607152217"
     */
    private int compareVersions(String v1, String v2) {
        String n1 = normalizeVersion(v1);
        String n2 = normalizeVersion(v2);

        // 尝试按点分版本号比较
        String[] parts1 = n1.split("\\.");
        String[] parts2 = n2.split("\\.");
        int maxLen = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLen; i++) {
            String p1 = i < parts1.length ? parts1[i] : "0";
            String p2 = i < parts2.length ? parts2[i] : "0";
            try {
                long num1 = Long.parseLong(p1);
                long num2 = Long.parseLong(p2);
                if (num1 != num2) return Long.compare(num1, num2);
            } catch (NumberFormatException e) {
                // 非数字部分按字符串比较
                int cmp = p1.compareTo(p2);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }

    /**
     * 标准化版本号：去掉前缀 V/v
     */
    private String normalizeVersion(String version) {
        if (version == null) return "0";
        return version.replaceFirst("^[Vv]", "");
    }

    /**
     * 从数据库读取配置值
     */
    private String getConfigValue(String key, String defaultValue) {
        try {
            LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysConfig::getConfigKey, key);
            SysConfig config = sysConfigMapper.selectOne(queryWrapper);
            if (config != null && config.getConfigValue() != null) {
                return config.getConfigValue();
            }
        } catch (Exception e) {
            log.warn("读取配置 {} 异常: {}, 使用默认值: {}", key, e.getMessage(), defaultValue);
        }
        return defaultValue;
    }

    /**
     * 获取版本校验配置（供API返回）
     */
    public Map<String, Object> getVersionCheckConfig() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("enabled", "true".equals(getConfigValue("version-check.enabled", "false")));
        config.put("mode", getConfigValue("version-check.mode", "none"));
        config.put("minVersion", getConfigValue("version-check.min-version", "0.0.0"));
        config.put("downloadUrl", getConfigValue("version-check.download-url", ""));
        config.put("success", true);
        return config;
    }

    /**
     * 更新版本校验配置
     */
    public Map<String, Object> updateVersionCheckConfig(Map<String, String> body) {
        java.util.List<String> updatableKeys = java.util.Arrays.asList(
            "version-check.enabled", "version-check.mode", "version-check.min-version", "version-check.download-url"
        );

        for (String key : updatableKeys) {
            if (body.containsKey(key)) {
                String value = body.get(key);
                updateConfigValue(key, value);
                log.info("更新配置: {} = {}", key, value);
            }
        }

        return getVersionCheckConfig();
    }

    /**
     * 更新单个配置值（upsert）
     */
    private void updateConfigValue(String key, String value) {
        LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysConfig::getConfigKey, key);
        SysConfig config = sysConfigMapper.selectOne(queryWrapper);
        if (config != null) {
            config.setConfigValue(value);
            config.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.updateById(config);
        } else {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigGroup("version-check");
            sysConfigMapper.insert(config);
        }
    }

    // ==================== 原有方法 ====================

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
