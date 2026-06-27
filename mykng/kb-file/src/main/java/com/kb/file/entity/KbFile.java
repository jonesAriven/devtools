package com.kb.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`file`")
public class KbFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long folderId;

    private Long userId;

    private String name;

    private String type;

    private Long size;

    private String minioPath;

    private String parseStatus;

    private String parseError;

    private Integer starred;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
