package com.jones.kb.dto.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileMergeRequest {

    @NotBlank(message = "文件标识不能为空")
    private String fileId;

    @NotBlank(message = "文件名不能为空")
    private String name;

    @NotNull(message = "文件夹ID不能为空")
    private Long folderId;

    private Long size;

    private Integer totalChunks;
}
