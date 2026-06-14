package com.jones.kb.mongo.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "file_content")
public class FileContent {

    @Id
    private String id;

    private Long fileId;

    private Long userId;

    private String title;

    private String content;

    private Integer version;

    private Boolean isCurrent;

    private LocalDateTime createdAt;
}
