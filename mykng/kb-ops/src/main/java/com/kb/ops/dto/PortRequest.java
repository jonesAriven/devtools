package com.kb.ops.dto;

import lombok.Data;

@Data
public class PortRequest {

    private Long hostId;

    private String port;

    private String protocol;

    private Long serviceId;

    private String purpose;

    private Integer status;

    private Integer exposed;

    private String remark;
}
