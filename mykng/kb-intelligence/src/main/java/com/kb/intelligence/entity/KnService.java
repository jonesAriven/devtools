package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_service")
public class KnService {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hostId;
    private String name;
    private String serviceType;
    private String version;
    private String installPath;
    private String configPath;
    private String logPath;
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
