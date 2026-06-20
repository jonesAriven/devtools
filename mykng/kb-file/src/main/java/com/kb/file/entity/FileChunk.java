package com.kb.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_chunk")
public class FileChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileId;

    private Integer chunkNumber;

    private String chunkPath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
