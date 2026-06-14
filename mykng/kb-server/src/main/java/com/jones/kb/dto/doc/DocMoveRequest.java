package com.jones.kb.dto.doc;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DocMoveRequest {

    @NotNull(message = "目标文件夹ID不能为空")
    private Long folderId;
}
