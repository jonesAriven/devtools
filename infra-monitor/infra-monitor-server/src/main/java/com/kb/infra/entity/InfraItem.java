package com.kb.infra.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Document(collection = "infra_items")
public class InfraItem {

    @Id
    private String id;

    @Indexed
    private String type;

    private String name;

    @Indexed
    private String category;

    private String description;

    private Map<String, Object> extra = new HashMap<>();

    @Indexed
    private Integer sortOrder;

    @Indexed
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
