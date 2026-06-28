package com.kb.intelligence.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class DocContentVO {
    private Long docId;
    private String title;
    private String plainText;
    private Integer wordCount;
    private List<SectionVO> sections;

    @Data
    public static class SectionVO {
        private String title;
        private Integer level;
        private String content;
        private Integer wordCount;
    }
}
