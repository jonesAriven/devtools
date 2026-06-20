package com.kb.knowledge.dto.doc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocCreateRequest {

    @NotNull(message = "文件夹ID不能为空")
    private Long folderId;

    @NotBlank(message = "文档标题不能为空")
    private String title;

    private String content;
}
