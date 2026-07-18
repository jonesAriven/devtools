package com.kb.portal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portal_system")
public class PortalSystem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String url;

    /** 公网入口(域名) - Stage 1 新增 */
    private String urlPublic;

    /** 家庭局域网入口(192.168.31.x) - Stage 1 新增 */
    private String urlLan;

    /** Tailscale入口(100.x.x.x) - Stage 1 新增 */
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

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
