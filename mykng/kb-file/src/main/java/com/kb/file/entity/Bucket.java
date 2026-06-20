package com.kb.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bucket")
public class Bucket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    private Integer lifecycleDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
