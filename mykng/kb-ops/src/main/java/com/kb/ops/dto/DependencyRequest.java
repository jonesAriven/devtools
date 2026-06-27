package com.kb.ops.dto;

import lombok.Data;

@Data
public class DependencyRequest {

    private Long serviceId;

    private String serviceName;

    private Long dependsOnServiceId;

    private String dependsOnServiceName;

    private String dependencyType;

    private String description;
}
