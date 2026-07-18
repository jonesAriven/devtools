package com.kb.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortalSystemRequest {

    @NotBlank(message = "系统名称不能为空")
    private String name;

    private String description;

    /** 主 URL - 兼容旧字段，允许留空（三入口至少填一个即可） */
    private String url;

    /** 公网入口(域名) */
    private String urlPublic;

    /** 家庭局域网入口(192.168.31.x) */
    private String urlLan;

    /** Tailscale入口(100.x.x.x) */
    private String urlTailscale;

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
