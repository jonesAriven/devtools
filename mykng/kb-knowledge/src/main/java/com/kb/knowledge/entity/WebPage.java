package com.kb.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("web_page")
public class WebPage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long folderId;

    private Long userId;

    private String url;

    private String title;

    private String snapshotPath;

    private Integer starred;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
