package com.jones.activation.controller;

import com.jones.activation.dto.GenerateRequest;
import com.jones.activation.dto.GenerateResponse;
import com.jones.activation.dto.VerifyRequest;
import com.jones.activation.dto.VerifyResponse;
import com.jones.activation.entity.ActivationLog;
import com.jones.activation.entity.ActivationRecord;
import com.jones.activation.service.ActivationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/activecode/api/activation")
public class ActivationController {

    private static final Logger log = LoggerFactory.getLogger(ActivationController.class);

    private final ActivationService activationService;

    public ActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping("/generate")
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        log.info("收到生成激活码请求, 序列号: {}", request.getSerialNumber());
        return activationService.generateActivationCode(request);
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        log.info("收到验证激活码请求");
        return activationService.verifyActivationCode(request);
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return activationService.queryRecords(keyword, status, page, size);
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) Long recordId,
                                    @RequestParam(required = false) String serialNumber,
                                    @RequestParam(required = false) String eventType,
                                    @RequestParam(required = false) String deviceId,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return activationService.queryLogs(recordId, serialNumber, eventType, deviceId, startDate, endDate, page, size);
    }

    @GetMapping("/parse-code")
    public Map<String, Object> parseCode(@RequestParam String activationCode) {
        log.info("收到解析激活码请求");
        return activationService.parseActivationCode(activationCode);
    }

    @GetMapping("/parse-serial")
    public Map<String, Object> parseSerial(@RequestParam String serialNumber) {
        log.info("收到解析序列号请求, 序列号: {}", serialNumber);
        return activationService.parseSerialNumberInfo(serialNumber);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        log.info("收到删除激活码记录请求, id: {}", id);
        boolean deleted = activationService.deleteRecord(id);
        return Map.of("success", deleted, "message", deleted ? "删除成功" : "记录不存在");
    }
    @DeleteMapping("/batch")
    public Map<String, Object> batchDelete(@RequestBody List<Long> ids) {
        log.info("收到批量删除激活码记录请求, ids: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Map.of("success", false, "message", "请选择要删除的记录");
        }
        if (ids.size() > 100) {
            return Map.of("success", false, "message", "单次批量删除不能超过100条");
        }
        Map<String, Object> result = activationService.batchDeleteRecords(ids);
        log.info("批量删除完成, 成功: {}", result.get("deletedCount"));
        return result;
    }

    @PutMapping("/{id}/alias")
    public Map<String, Object> updateAlias(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String alias = body.get("deviceAlias");
        log.info("收到修改设备别名请求, id: {}, alias: {}", id, alias);
        return activationService.updateDeviceAlias(id, alias);
    }


    @GetMapping("/config/default-expire")
    public Map<String, Object> getDefaultExpireConfig() {
        log.info("获取默认有效期配置");
        return activationService.getDefaultExpireConfig();
    }

    @PutMapping("/config/default-expire")
    public Map<String, Object> updateDefaultExpireConfig(@RequestBody Map<String, Object> body) {
        int minutes = Integer.parseInt(body.getOrDefault("expireMinutes", "43200").toString());
        log.info("更新默认有效期配置: {} 分钟", minutes);
        return activationService.updateDefaultExpireConfig(minutes);
    }

    @GetMapping("/version-check")
    public Map<String, Object> getVersionCheckConfig() {
        log.info("获取版本校验配置");
        return activationService.getVersionCheckConfig();
    }

    @PutMapping("/version-check")
    public Map<String, Object> updateVersionCheckConfig(@RequestBody Map<String, String> body) {
        log.info("更新版本校验配置: {}", body.keySet());
        return activationService.updateVersionCheckConfig(body);
    }
}
