package com.jones.kb.dto.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FolderCreateRequest {

    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    private Long parentId;

    @NotBlank(message = "文件夹名称不能为空")
    private String name;

    private Integer sortOrder;
}
