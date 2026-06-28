package com.kb.knowledge.builder;

import com.kb.knowledge.entity.Share;

import java.time.LocalDateTime;

/**
 * 分享测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class ShareBuilder {

    private Long id = 1L;
    private Long userId = 1L;
    private String resourceType = "DOC";
    private Long resourceId = 1L;
    private String code = "share-code-001";
    private String extractCode;
    private LocalDateTime expireAt = LocalDateTime.now().plusDays(7);
    private Integer viewCount = 0;
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private ShareBuilder() {}

    /**
     * 默认分享
     */
    public static ShareBuilder aShare() {
        return new ShareBuilder();
    }

    /**
     * 带提取密码的分享
     */
    public static ShareBuilder aPasswordProtectedShare() {
        return new ShareBuilder().withExtractCode("1234");
    }

    /**
     * 已过期的分享
     */
    public static ShareBuilder anExpiredShare() {
        return new ShareBuilder().withExpireAt(LocalDateTime.now().minusDays(1));
    }

    /**
     * 永久分享（无过期时间）
     */
    public static ShareBuilder aPermanentShare() {
        return new ShareBuilder().withExpireAt(null);
    }

    public ShareBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public ShareBuilder withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public ShareBuilder withResourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    public ShareBuilder withResourceId(Long resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    public ShareBuilder withCode(String code) {
        this.code = code;
        return this;
    }

    public ShareBuilder withExtractCode(String extractCode) {
        this.extractCode = extractCode;
        return this;
    }

    public ShareBuilder withExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
        return this;
    }

    public ShareBuilder withViewCount(Integer viewCount) {
        this.viewCount = viewCount;
        return this;
    }

    public ShareBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public ShareBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public ShareBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Share build() {
        Share share = new Share();
        share.setId(id);
        share.setUserId(userId);
        share.setResourceType(resourceType);
        share.setResourceId(resourceId);
        share.setCode(code);
        share.setExtractCode(extractCode);
        share.setExpireAt(expireAt);
        share.setViewCount(viewCount);
        share.setDeleted(deleted);
        share.setCreatedAt(createdAt);
        share.setUpdatedAt(updatedAt);
        return share;
    }
}
