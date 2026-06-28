package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_command")
public class KnCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String command;
    private String description;
    private String category;
    private String riskLevel;
    private String osType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
