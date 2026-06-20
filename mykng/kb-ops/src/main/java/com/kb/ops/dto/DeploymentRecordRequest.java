package com.kb.ops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeploymentRecordRequest {

    @NotNull(message = "服务ID不能为空")
    private Long serviceId;

    private Long hostId;

    private String version;

    private String previousVersion;

    private String operator;

    /** 1=成功 0=失败 */
    private Integer result;

    /** 0=普通部署 1=回滚操作 */
    private Integer rollback;

    private String rollbackInfo;

    private String remark;
}
