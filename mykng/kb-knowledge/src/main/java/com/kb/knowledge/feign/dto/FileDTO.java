package com.kb.knowledge.feign.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息 DTO（通过 Feign 从 kb-file 获取）
 */
@Data
public class FileDTO {

    private Long id;

    private Long folderId;

    private Long userId;

    private String name;

    private String type;

    private Long size;

    private String minioPath;

    private String parseStatus;

    private Integer starred;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
