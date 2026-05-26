package com.frp.manager.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("frp_server")
public class FrpServer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer bindPort;
    private String token;
    private Integer dashboardPort;
    private String dashboardUser;
    private String dashboardPwd;
    private Integer vhostHttpPort;
    private String remark;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
