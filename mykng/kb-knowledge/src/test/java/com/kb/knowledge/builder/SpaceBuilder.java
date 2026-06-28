package com.kb.knowledge.builder;

import com.kb.knowledge.entity.Space;

import java.time.LocalDateTime;

/**
 * 空间测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class SpaceBuilder {

    private Long id = 1L;
    private Long userId = 1L;
    private String name = "默认空间";
    private String type = "PERSONAL";
    private String description = "个人默认知识空间";
    private Integer status = 1;
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private SpaceBuilder() {}

    /**
     * 默认空间
     */
    public static SpaceBuilder aSpace() {
        return new SpaceBuilder();
    }

    /**
     * 个人空间
     */
    public static SpaceBuilder aPersonalSpace() {
        return new SpaceBuilder()
                .withType("PERSONAL")
                .withName("我的个人空间");
    }

    /**
     * 团队空间
     */
    public static SpaceBuilder aTeamSpace() {
        return new SpaceBuilder()
                .withType("TEAM")
                .withName("团队协作空间");
    }

    public SpaceBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public SpaceBuilder withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public SpaceBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public SpaceBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public SpaceBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public SpaceBuilder withStatus(Integer status) {
        this.status = status;
        return this;
    }

    public SpaceBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public SpaceBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public SpaceBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Space build() {
        Space space = new Space();
        space.setId(id);
        space.setUserId(userId);
        space.setName(name);
        space.setType(type);
        space.setDescription(description);
        space.setStatus(status);
        space.setDeleted(deleted);
        space.setCreatedAt(createdAt);
        space.setUpdatedAt(updatedAt);
        return space;
    }
}
