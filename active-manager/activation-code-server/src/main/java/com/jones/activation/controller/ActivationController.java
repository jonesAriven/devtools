package com.jones.activation.controller;

import com.jones.activation.dto.GenerateRequest;
import com.jones.activation.dto.GenerateResponse;
import com.jones.activation.dto.VerifyRequest;
import com.jones.activation.dto.VerifyResponse;
import com.jones.activation.service.ActivationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activation")
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
}
