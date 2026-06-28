package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_credential")
public class KnCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hostId;
    private Long serviceId;
    private String credType;
    private String username;
    private String passwordEncrypted;
    private String accessKey;
    private String secretKeyEncrypted;
    private String token;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
