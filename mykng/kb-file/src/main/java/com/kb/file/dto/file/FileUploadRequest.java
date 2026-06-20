package com.kb.file.dto.file;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileUploadRequest {

    @NotNull(message = "文件夹ID不能为空")
    private Long folderId;

    private String fileId;

    private Integer chunkNumber;

    private Integer totalChunks;
}
