package com.kb.intelligence.mongo.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "kn_content")
public class KnContent {
    @Id
    private String id;
    @Indexed(unique = true)
    private Long docId;
    private List<Section> sections;
    private String plainText;
    private Integer wordCount;

    @Data
    public static class Section {
        private String title;
        private Integer level;
        private String content;
        private Integer wordCount;
        private List<String> entities;
        private List<String> commands;
    }
}
