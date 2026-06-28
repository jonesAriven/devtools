package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_doc_entity_ref")
public class KnDocEntityRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String entityType;
    private Long entityId;
    private String sourceSection;
    private Integer confidence;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
