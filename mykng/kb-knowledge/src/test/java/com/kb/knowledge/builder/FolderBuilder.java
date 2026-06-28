package com.kb.knowledge.builder;

import com.kb.knowledge.entity.Folder;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 文件夹测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class FolderBuilder {

    private Long id = 1L;
    private Long spaceId = 1L;
    private Long parentId = 0L;
    private String name = "测试文件夹";
    private Integer sortOrder = 0;
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private FolderBuilder() {}

    /**
     * 默认文件夹
     */
    public static FolderBuilder aFolder() {
        return new FolderBuilder();
    }

    /**
     * 根文件夹（parentId=0）
     */
    public static FolderBuilder aRootFolder() {
        return new FolderBuilder().withParentId(0L).withName("根目录");
    }

    /**
     * 子文件夹
     */
    public static FolderBuilder aChildFolder(Long parentId) {
        return new FolderBuilder().withParentId(parentId).withName("子文件夹");
    }

    public FolderBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public FolderBuilder withSpaceId(Long spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public FolderBuilder withParentId(Long parentId) {
        this.parentId = parentId;
        return this;
    }

    public FolderBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public FolderBuilder withSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public FolderBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public FolderBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public FolderBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Folder build() {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setSpaceId(spaceId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setSortOrder(sortOrder);
        folder.setDeleted(deleted);
        folder.setCreatedAt(createdAt);
        folder.setUpdatedAt(updatedAt);
        folder.setChildren(Collections.emptyList());
        return folder;
    }
}
