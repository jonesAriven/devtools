package com.kb.ops.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DomainRequest {

    private String domain;

    private String type;

    private String purpose;

    private String registrar;

    private LocalDateTime expiresAt;

    private LocalDateTime sslExpiresAt;

    private Integer status;

    private String remark;
}
