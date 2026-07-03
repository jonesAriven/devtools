package com.kb.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServiceRequest {

    @NotBlank(message = "服务名称不能为空")
    private String name;

    private String type;

    private String version;

    private Integer port;

    private Long hostId;

    private String deployPath;

    private Integer status;

    private String dependencies;

    private String tags;

    private String remark;
}
