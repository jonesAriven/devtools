package com.kb.knowledge.dto.doc;

import lombok.Data;

@Data
public class DocUpdateRequest {

    private String title;

    private String content;

    /** 文档格式：html / markdown（允许在编辑时切换格式，可选） */
    private String format;
}
