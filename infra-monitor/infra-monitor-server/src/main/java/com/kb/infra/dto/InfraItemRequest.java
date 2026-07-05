package com.kb.infra.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class InfraItemRequest {
    @NotBlank(message = "类型不能为空")
    private String type;

    @NotBlank(message = "名称不能为空")
    private String name;

    private String category;

    private String description;

    private Map<String, Object> extra;

    private Integer sortOrder;
}
