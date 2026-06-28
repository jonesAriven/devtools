package com.kb.intelligence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kn_domain")
public class KnDomain {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domain;
    private String subDomain;
    private Long targetHostId;
    private Integer targetPort;
    private String targetService;
    private String dnsType;
    private String status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
