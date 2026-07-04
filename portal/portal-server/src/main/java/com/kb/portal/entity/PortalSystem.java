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

    private String icon;

    private String category;

    private Integer status;

    private String healthCheckUrl;

    private String docs;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
