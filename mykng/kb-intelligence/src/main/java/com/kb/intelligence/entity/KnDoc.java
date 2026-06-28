package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_doc")
public class KnDoc {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceId;
    private String title;
    private String filePath;
    private String docType;
    private String category;
    private String tags;
    private String summary;
    private String contentHash;
    private Integer entityCount;
    private Integer commandCount;
    private Integer sectionCount;
    private Integer wordCount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
