package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_host")
public class KnHost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String ip;
    private String tailscaleIp;
    private String publicIp;
    private Integer sshPort;
    private String username;
    private String passwordEncrypted;
    private String osType;
    private String osVersion;
    private String cpuArch;
    private Integer cpuCores;
    private Long memoryGb;
    private String role;
    private String environment;
    private String location;
    private String status;
    private String tags;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
