package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运维看板快照
 */
@Data
@TableName("ops_snapshot")
public class OpsSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate snapshotDate;

    /** 指标键: host_total/service_total 等 */
    private String metricKey;

    private Long metricValue;

    private String extra;

    private LocalDateTime createdAt;
}
