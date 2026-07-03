package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 端口管理
 */
@Data
@TableName("ops_port")
public class Port {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联主机ID */
    private Long hostId;

    /** 端口号 */
    private Integer port;

    /** 协议: TCP/UDP */
    private String protocol;

    /** 关联服务ID(可空) */
    private Long serviceId;

    /** 用途说明 */
    private String purpose;

    /** 1=开放 0=关闭 */
    private Integer status;

    /** 是否对外暴露 0=否 1=是 */
    private Integer exposed;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
