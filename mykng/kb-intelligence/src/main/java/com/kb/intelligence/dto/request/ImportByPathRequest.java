package com.kb.intelligence.dto.request;

import lombok.Data;

@Data
public class ImportByPathRequest {
    private String path;
    private Boolean incremental = true;
}
