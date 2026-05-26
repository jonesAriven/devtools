package com.frp.manager.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("frp_client")
public class FrpClient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serverId;
    private String name;
    private String host;
    private String configPath;
    private String configFormat;
    private String sshHost;
    private Integer sshPort;
    private String sshUser;
    private String sshPwd;
    private String osType;
    private String frpcCmd;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
