package com.jones.kb.dto.space;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SpaceCreateRequest {

    @NotBlank(message = "空间名称不能为空")
    private String name;

    private String type;

    private String description;
}
