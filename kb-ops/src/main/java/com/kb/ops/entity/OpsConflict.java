package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 矛盾检测结果
 */
@Data
@TableName("ops_conflict")
public class OpsConflict {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码: VERSION_MISMATCH/PORT_CONFLICT 等 */
    private String ruleCode;

    private String ruleName;

    /** 严重程度: 1=提示 2=警告 3=严重 */
    private Integer severity;

    /** 对象类型: HOST/SERVICE */
    private String targetType;

    private Long targetId;

    private String targetName;

    private String detail;

    /** 0=未处理 1=已忽略 2=已解决 */
    private Integer status;

    private LocalDateTime detectedAt;

    private LocalDateTime createdAt;
}
