package com.jones.kb.mongo.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "ai_summary")
public class AiSummary {

    @Id
    private String id;

    private String bizType;

    private Long bizId;

    private String summary;

    private String model;

    private LocalDateTime createdAt;
}
