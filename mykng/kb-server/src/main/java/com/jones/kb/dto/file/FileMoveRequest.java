package com.jones.kb.dto.file;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileMoveRequest {

    @NotNull(message = "目标文件夹ID不能为空")
    private Long folderId;
}
