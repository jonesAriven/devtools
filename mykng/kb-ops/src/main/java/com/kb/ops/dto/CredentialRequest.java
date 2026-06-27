package com.kb.ops.dto;

import lombok.Data;

@Data
public class CredentialRequest {

    private String name;

    private String type;

    private String username;

    private String password;

    private String secretKey;

    private Long hostId;

    private Long serviceId;

    private String remark;
}
