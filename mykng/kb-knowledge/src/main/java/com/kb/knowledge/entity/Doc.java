package com.kb.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc")
public class Doc {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long folderId;

    private Long userId;

    private String title;

    /** 文档格式：html / markdown */
    private String format;

    private Integer starred;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String content;

    @TableField(exist = false)
    private Long spaceId;

    @TableField(exist = false)
    private Integer wordCount;
}
