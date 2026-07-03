package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 域名管理
 */
@Data
@TableName("ops_domain")
public class Domain {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 域名 */
    private String domain;

    /** 类型: 顶级域/子域 */
    private String type;

    /** 用途 */
    private String purpose;

    /** 注册商 */
    private String registrar;

    /** 域名到期时间 */
    private LocalDateTime expiresAt;

    /** SSL证书到期时间 */
    private LocalDateTime sslExpiresAt;

    /** 1=正常 0=过期 2=即将过期 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
