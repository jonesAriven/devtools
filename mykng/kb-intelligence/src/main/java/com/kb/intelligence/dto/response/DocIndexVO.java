package com.kb.intelligence.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class DocIndexVO {
    private Long id;
    private String title;
    private String sourceId;
    private String filePath;
    private String docType;
    private String category;
    private String tags;
    private String summary;
    private Integer entityCount;
    private Integer commandCount;
    private Integer sectionCount;
    private Integer wordCount;
    private String createdAt;
    private String updatedAt;
}
