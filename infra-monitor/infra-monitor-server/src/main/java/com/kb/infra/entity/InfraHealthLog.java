package com.kb.infra.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "infra_health_logs")
public class InfraHealthLog {

    @Id
    private String id;

    @Indexed
    private String serviceId;

    private String serviceName;

    private String checkUrl;

    @Indexed
    private String status;

    private Integer latencyMs;

    private String errorMsg;

    @Indexed
    private LocalDateTime checkedAt;
}
