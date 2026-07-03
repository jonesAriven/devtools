package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String detail;
    private String ip;
    private String userAgent;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
