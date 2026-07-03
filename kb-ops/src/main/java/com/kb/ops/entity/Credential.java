package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭据管理
 */
@Data
@TableName("ops_credential")
public class Credential {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 凭据名称 */
    private String name;

    /** 类型: SSH/DB/API_TOKEN/OTHER */
    private String type;

    private String username;

    /** 加密后的密码(AES-256-GCM) */
    private String passwordEncrypted;

    /** API key 类密钥(加密存储) */
    private String secretKey;

    /** 关联主机ID(可空) */
    private Long hostId;

    /** 关联服务ID(可空) */
    private Long serviceId;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
