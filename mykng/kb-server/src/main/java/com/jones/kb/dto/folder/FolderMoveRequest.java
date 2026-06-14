package com.jones.kb.dto.folder;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FolderMoveRequest {

    @NotNull(message = "目标父文件夹ID不能为空")
    private Long parentId;
}
