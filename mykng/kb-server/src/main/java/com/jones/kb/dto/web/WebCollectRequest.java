package com.jones.kb.dto.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebCollectRequest {

    @NotBlank(message = "URL不能为空")
    private String url;

    @NotNull(message = "文件夹ID不能为空")
    private Long folderId;
}
