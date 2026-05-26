package com.frp.manager.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("frp_tunnel")
public class FrpTunnel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clientId;
    private String name;
    private String type;
    private String localIp;
    private Integer localPort;
    private Integer remotePort;
    private Integer useEncryption;
    private Integer useCompression;
    private Integer status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
