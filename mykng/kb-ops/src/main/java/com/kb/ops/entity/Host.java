package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维主机
 */
@Data
@TableName("ops_host")
public class Host {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String ip;

    private String tailscaleIp;

    private Integer sshPort;

    private String username;

    /** AES-256-GCM 加密后的密码 */
    private String passwordEncrypted;

    /** 角色: web/db/cache/app 等 */
    private String role;

    /** 1=运行中 0=停机 2=维护中 */
    private Integer status;

    /** 标签，逗号分隔 */
    private String tags;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
