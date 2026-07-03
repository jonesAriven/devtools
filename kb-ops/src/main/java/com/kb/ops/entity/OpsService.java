package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维服务
 */
@Data
@TableName("ops_service")
public class OpsService {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 服务类型: web/db/cache/mq 等 */
    private String type;

    private String version;

    private Integer port;

    private Long hostId;

    private String deployPath;

    /** 1=运行中 0=已停止 2=异常 */
    private Integer status;

    /** 依赖服务，逗号分隔的 service name */
    private String dependencies;

    private String tags;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
