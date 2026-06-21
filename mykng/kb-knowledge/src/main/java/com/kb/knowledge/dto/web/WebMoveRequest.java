package com.kb.knowledge.dto.web;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebMoveRequest {

    @NotNull(message = "目标文件夹ID不能为空")
    private Long folderId;
}
