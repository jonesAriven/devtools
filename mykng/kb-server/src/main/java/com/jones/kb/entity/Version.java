package com.jones.kb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("version")
public class Version {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String resourceType;

    private Long resourceId;

    private Integer versionNum;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
