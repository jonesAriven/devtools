package com.kb.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortalSystemRequest {

    @NotBlank(message = "系统名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "系统URL不能为空")
    private String url;

    private String icon;

    private String color;

    private String category;

    private Integer status;

    private String healthCheckUrl;

    private String docs;

    private String downloadPath;

    private String techStack;

    private String loginUsername;

    private String loginPassword;

    private Integer sortOrder;
}
