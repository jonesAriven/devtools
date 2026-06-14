package com.jones.kb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("share_access_log")
public class ShareAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shareId;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime accessedAt;
}
