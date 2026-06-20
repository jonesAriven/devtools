package com.kb.knowledge.mongo.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "web_content")
public class WebContent {

    @Id
    private String id;

    private Long webId;

    private Long userId;

    private String url;

    private String title;

    private String content;

    private Integer version;

    private Boolean isCurrent;

    private LocalDateTime createdAt;
}
