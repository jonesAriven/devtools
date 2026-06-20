package com.kb.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OpsKnowledgeRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String category;

    private String content;

    private String tags;

    private Long hostId;

    private Long serviceId;

    private String author;
}
