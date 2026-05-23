package com.jones.activation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.activation.dto.GenerateRequest;
import com.jones.activation.dto.GenerateResponse;
import com.jones.activation.dto.VerifyRequest;
import com.jones.activation.dto.VerifyResponse;
import com.jones.activation.entity.ActivationLog;
import com.jones.activation.entity.ActivationRecord;
import com.jones.activation.mapper.ActivationLogMapper;
import com.jones.activation.mapper.ActivationRecordMapper;
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

    public ActivationService(CryptoUtil cryptoUtil, ActivationRecordMapper activationRecordMapper,
                             ActivationLogMapper activationLogMapper) {
        this.cryptoUtil = cryptoUtil;
        this.activationRecordMapper = activationRecordMapper;
        this.activationLogMapper = activationLogMapper;
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

        CryptoUtil.SerialNumberParseResult parseResult = CryptoUtil.decryptSerialNumber(serialNumber);
        if (parseResult.isSuccess()) {
            initialSerial = parseResult.getInitialSerial();
            String parsedDeviceId = parseResult.getDeviceId();
            machineCode = parseResult.getMachineCode();

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

    public List<ActivationRecord> queryRecords(String keyword, String status) {
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

        return activationRecordMapper.selectList(queryWrapper);
    }

    public List<ActivationLog> queryLogs(Long recordId, String serialNumber, String eventType) {
        LambdaQueryWrapper<ActivationLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ActivationLog::getCreateTime);

        if (recordId != null) {
            queryWrapper.eq(ActivationLog::getRecordId, recordId);
        }
        if (serialNumber != null && !serialNumber.trim().isEmpty()) {
            queryWrapper.eq(ActivationLog::getSerialNumber, serialNumber);
        }
        if (eventType != null && !eventType.trim().isEmpty()) {
            queryWrapper.eq(ActivationLog::getEventType, eventType);
        }

        queryWrapper.last("LIMIT 200");
        List<ActivationLog> logs = activationLogMapper.selectList(queryWrapper);

        // 关联查询设备别名：通过 serial_number 关联 activation_record 获取 device_alias
        for (ActivationLog logEntry : logs) {
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

        return logs;
    }

    public boolean deleteRecord(Long id) {
        return activationRecordMapper.deleteById(id) > 0;
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
        String[] parsed = parseSerialNumber(serialNumber);
        map.put("serialNumber", serialNumber);
        map.put("initialSerial", parsed[0]);
        map.put("machineCode", parsed[1]);

        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivationRecord::getSerialNumber, serialNumber);
        ActivationRecord record = activationRecordMapper.selectOne(queryWrapper);
        if (record != null) {
            map.put("recordId", record.getId());
            map.put("deviceId", record.getDeviceId());
            map.put("expireTime", record.getExpireTime());
            map.put("expireMinutes", record.getExpireMinutes());
            map.put("activatedTime", record.getActivatedTime() != null ? record.getActivatedTime().toString() : null);
            map.put("createTime", record.getCreateTime() != null ? record.getCreateTime().toString() : null);
            map.put("expired", record.getExpireTime() < System.currentTimeMillis());
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
