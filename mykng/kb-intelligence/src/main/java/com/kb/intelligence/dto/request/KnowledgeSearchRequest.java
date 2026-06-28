package com.kb.intelligence.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class KnowledgeSearchRequest {
    private String query;
    private List<String> docTypes;
    private List<String> entityTypes;
    private List<String> tags;
    private Integer page = 1;
    private Integer size = 20;
    private Boolean useVector = false;
}
