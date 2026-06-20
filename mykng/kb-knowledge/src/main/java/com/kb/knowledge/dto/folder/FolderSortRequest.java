package com.kb.knowledge.dto.folder;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FolderSortRequest {

    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;
}
