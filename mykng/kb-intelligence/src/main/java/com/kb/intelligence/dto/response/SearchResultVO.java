package com.kb.intelligence.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class SearchResultVO {
    private Long docId;
    private String docTitle;
    private String docType;
    private String category;
    private String highlight;
    private Float score;
    private List<String> matchedSections;
}
