package com.kb.knowledge.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TagBindRequest {

    @NotNull(message = "标签ID不能为空")
    private Long tagId;

    @NotBlank(message = "资源类型不能为空")
    private String resourceType;

    @NotNull(message = "资源ID不能为空")
    private Long resourceId;
}
