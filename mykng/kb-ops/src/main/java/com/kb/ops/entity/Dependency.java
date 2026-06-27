package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务依赖关系
 */
@Data
@TableName("ops_dependency")
public class Dependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 依赖方服务ID */
    private Long serviceId;

    /** 依赖方服务名(冗余) */
    private String serviceName;

    /** 被依赖服务ID */
    private Long dependsOnServiceId;

    /** 被依赖服务名(冗余) */
    private String dependsOnServiceName;

    /** 依赖类型: REQUIRED/OPTIONAL/WEAK */
    private String dependencyType;

    /** 依赖描述 */
    private String description;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
