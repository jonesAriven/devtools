package com.kb.knowledge.mongo.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "doc_content")
public class DocContent {

    @Id
    private String id;

    private Long docId;

    private Long userId;

    private String content;

    private Integer version;

    private Boolean isCurrent;

    private LocalDateTime createdAt;
}
