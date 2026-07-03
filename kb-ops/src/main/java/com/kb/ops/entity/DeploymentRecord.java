package com.kb.ops.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部署/变更记录
 */
@Data
@TableName("ops_change_log")
public class DeploymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceId;

    private String serviceName;

    private Long hostId;

    private String version;

    private String previousVersion;

    private String operator;

    private LocalDateTime deployTime;

    /** 1=成功 0=失败 */
    private Integer result;

    /** 0=普通部署 1=回滚操作 */
    private Integer rollback;

    private String rollbackInfo;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
