package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_port")
public class KnPort {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hostId;
    private Long serviceId;
    private Integer port;
    private String protocol;
    private String mapping;
    private String accessUrl;
    private Integer exposed;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
