package com.kb.file.dto.file;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FileContentUpdateRequest {

    @NotNull(message = "文件内容不能为空")
    private String content;
}
