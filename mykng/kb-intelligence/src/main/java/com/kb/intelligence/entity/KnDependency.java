package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_dependency")
public class KnDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fromType;
    private Long fromId;
    private String toType;
    private Long toId;
    private String depType;
    private String protocol;
    private Integer port;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
