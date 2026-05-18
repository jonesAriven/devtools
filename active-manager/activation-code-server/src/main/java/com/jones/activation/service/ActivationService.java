package com.jones.activation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.activation.dto.GenerateRequest;
import com.jones.activation.dto.GenerateResponse;
import com.jones.activation.dto.VerifyRequest;
import com.jones.activation.dto.VerifyResponse;
import com.jones.activation.entity.ActivationRecord;
import com.jones.activation.mapper.ActivationRecordMapper;
import com.jones.activation.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);

    private final CryptoUtil cryptoUtil;
    private final ActivationRecordMapper activationRecordMapper;

    public ActivationService(CryptoUtil cryptoUtil, ActivationRecordMapper activationRecordMapper) {
        this.cryptoUtil = cryptoUtil;
        this.activationRecordMapper = activationRecordMapper;
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

        int expireDays = request.getExpireDays() != null ? request.getExpireDays() : 365;
        if (expireDays <= 0) {
            log.warn("生成激活码失败: 过期天数无效, expireDays={}", expireDays);
            return GenerateResponse.builder()
                    .success(false)
                    .message("过期天数必须大于0")
                    .build();
        }

        String deviceId = request.getDeviceId();
        long expireTimestamp = System.currentTimeMillis() + (long) expireDays * 24 * 60 * 60 * 1000;

        LambdaQueryWrapper<ActivationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivationRecord::getSerialNumber, serialNumber);
        ActivationRecord existingRecord = activationRecordMapper.selectOne(queryWrapper);

        if (existingRecord != null) {
            if (existingRecord.getExpireTime() > System.currentTimeMillis()) {
                log.info("序列号已存在且未过期, 序列号: {}, 设备ID: {}, 剩余有效期: {}天",
                        serialNumber, existingRecord.getDeviceId(),
                        (existingRecord.getExpireTime() - System.currentTimeMillis()) / (24 * 60 * 60 * 1000));
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

        if (existingRecord != null) {
            existingRecord.setActivationCode(activationCode);
            existingRecord.setDeviceId(deviceId);
            existingRecord.setExpireTime(expireTimestamp);
            existingRecord.setUpdateTime(LocalDateTime.now());
            activationRecordMapper.updateById(existingRecord);
            log.info("更新激活码记录, 序列号: {}, 设备ID: {}, 新过期时间: {}", serialNumber, deviceId, expireTimestamp);
        } else {
            ActivationRecord record = new ActivationRecord();
            record.setSerialNumber(serialNumber);
            record.setDeviceId(deviceId);
            record.setActivationCode(activationCode);
            record.setExpireTime(expireTimestamp);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            activationRecordMapper.insert(record);
            log.info("新增激活码记录, 序列号: {}, 设备ID: {}, 过期时间: {}", serialNumber, deviceId, expireTimestamp);
        }

        return GenerateResponse.builder()
                .success(true)
                .message("激活码生成成功")
                .activationCode(activationCode)
                .expireTime(expireTimestamp)
                .serialNumber(serialNumber)
                .deviceId(deviceId)
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

        String deviceId = request.getDeviceId();
        CryptoUtil.ActivationCodeParseResult result = cryptoUtil.parseAndVerify(activationCode, deviceId);

        if (!result.isValid()) {
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
            return VerifyResponse.builder()
                    .success(false)
                    .message("激活码已过期")
                    .serialNumber(result.getSerialNumber())
                    .deviceId(result.getDeviceId())
                    .expireTime(result.getExpireTimestamp())
                    .expired(true)
                    .build();
        }

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
}