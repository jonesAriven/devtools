package com.kb.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("share")
public class Share {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String resourceType;

    private Long resourceId;

    private String code;

    private String extractCode;

    private LocalDateTime expireAt;

    private Integer viewCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
