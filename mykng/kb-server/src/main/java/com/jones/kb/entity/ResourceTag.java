package com.jones.kb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_tag")
public class ResourceTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tagId;

    private String resourceType;

    private Long resourceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
