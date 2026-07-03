package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维知识
 */
@Data
@TableName("ops_knowledge")
public class OpsKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 分类: 部署/排障/巡检/规范等 */
    private String category;

    /** 内容 (Markdown) */
    private String content;

    private String tags;

    private Long hostId;

    private Long serviceId;

    private String author;

    private Integer viewCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
