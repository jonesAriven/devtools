package com.jones.kb.dto.share;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareCreateRequest {

    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    @NotNull(message = "资源ID不能为空")
    private Long resourceId;

    private String extractCode;

    private String expireAt;
}
