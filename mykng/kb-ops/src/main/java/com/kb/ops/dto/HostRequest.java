package com.kb.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HostRequest {

    @NotBlank(message = "主机名称不能为空")
    private String name;

    @NotBlank(message = "IP不能为空")
    private String ip;

    private String tailscaleIp;

    private Integer sshPort;

    private String username;

    /** 明文密码，服务端加密存储；为空表示不修改 */
    private String password;

    private String role;

    private Integer status;

    private String tags;

    private String remark;
}
