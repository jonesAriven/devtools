package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_timeline")
public class KnTimeline {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String eventTime;
    private String eventType;
    private String title;
    private String description;
    private String severity;
    private String status;
    private String solution;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
