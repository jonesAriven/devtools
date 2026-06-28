package com.kb.file.builder;

import com.kb.file.entity.KbFile;

import java.time.LocalDateTime;

/**
 * 文件测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class KbFileBuilder {

    private Long id = 1L;
    private Long folderId = 0L;
    private Long userId = 1L;
    private String name = "test-file.txt";
    private String type = "txt";
    private Long size = 1024L;
    private String minioPath = "kb/test-file.txt";
    private String parseStatus = "PENDING";
    private String parseError;
    private Integer starred = 0;
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private KbFileBuilder() {}

    /**
     * 默认文件
     */
    public static KbFileBuilder aKbFile() {
        return new KbFileBuilder();
    }

    /**
     * 已解析文件
     */
    public static KbFileBuilder aParsedFile() {
        return new KbFileBuilder().withParseStatus("SUCCESS");
    }

    /**
     * 已收藏文件
     */
    public static KbFileBuilder aStarredFile() {
        return new KbFileBuilder().withStarred(1);
    }

    /**
     * 已删除文件
     */
    public static KbFileBuilder aDeletedFile() {
        return new KbFileBuilder().withDeleted(1);
    }

    public KbFileBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public KbFileBuilder withFolderId(Long folderId) {
        this.folderId = folderId;
        return this;
    }

    public KbFileBuilder withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public KbFileBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public KbFileBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public KbFileBuilder withSize(Long size) {
        this.size = size;
        return this;
    }

    public KbFileBuilder withMinioPath(String minioPath) {
        this.minioPath = minioPath;
        return this;
    }

    public KbFileBuilder withParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
        return this;
    }

    public KbFileBuilder withParseError(String parseError) {
        this.parseError = parseError;
        return this;
    }

    public KbFileBuilder withStarred(Integer starred) {
        this.starred = starred;
        return this;
    }

    public KbFileBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public KbFileBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public KbFileBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public KbFile build() {
        KbFile file = new KbFile();
        file.setId(id);
        file.setFolderId(folderId);
        file.setUserId(userId);
        file.setName(name);
        file.setType(type);
        file.setSize(size);
        file.setMinioPath(minioPath);
        file.setParseStatus(parseStatus);
        file.setParseError(parseError);
        file.setStarred(starred);
        file.setDeleted(deleted);
        file.setCreatedAt(createdAt);
        file.setUpdatedAt(updatedAt);
        return file;
    }
}
