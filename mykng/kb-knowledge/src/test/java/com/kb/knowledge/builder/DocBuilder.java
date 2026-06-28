package com.kb.knowledge.builder;

import com.kb.knowledge.entity.Doc;

import java.time.LocalDateTime;

/**
 * 文档测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class DocBuilder {

    private Long id = 1L;
    private Long folderId = 1L;
    private Long userId = 1L;
    private String title = "测试文档";
    private Integer starred = 0;
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String content = "测试文档内容";
    private Long spaceId = 1L;
    private Integer wordCount = 6;

    private DocBuilder() {}

    /**
     * 默认文档
     */
    public static DocBuilder aDoc() {
        return new DocBuilder();
    }

    /**
     * 已发布文档
     */
    public static DocBuilder aPublishedDoc() {
        return new DocBuilder().withTitle("已发布文档");
    }

    /**
     * 草稿文档
     */
    public static DocBuilder aDraftDoc() {
        return new DocBuilder().withTitle("草稿文档").withContent("草稿内容，尚未完成");
    }

    /**
     * 已删除文档
     */
    public static DocBuilder aDeletedDoc() {
        return new DocBuilder().withDeleted(1).withTitle("已删除文档");
    }

    public DocBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public DocBuilder withFolderId(Long folderId) {
        this.folderId = folderId;
        return this;
    }

    public DocBuilder withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public DocBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public DocBuilder withStarred(Integer starred) {
        this.starred = starred;
        return this;
    }

    public DocBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public DocBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public DocBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public DocBuilder withContent(String content) {
        this.content = content;
        return this;
    }

    public DocBuilder withSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public DocBuilder withWordCount(Integer wordCount) {
        this.wordCount = wordCount;
        return this;
    }

    public Doc build() {
        Doc doc = new Doc();
        doc.setId(id);
        doc.setFolderId(folderId);
        doc.setUserId(userId);
        doc.setTitle(title);
        doc.setStarred(starred);
        doc.setDeleted(deleted);
        doc.setCreatedAt(createdAt);
        doc.setUpdatedAt(updatedAt);
        doc.setContent(content);
        doc.setSpaceId(spaceId);
        doc.setWordCount(wordCount);
        return doc;
    }
}
