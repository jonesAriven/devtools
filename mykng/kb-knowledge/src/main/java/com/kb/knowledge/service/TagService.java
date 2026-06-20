package com.kb.knowledge.service;

import com.kb.knowledge.dto.tag.TagBindRequest;
import com.kb.knowledge.entity.Tag;

import java.util.List;

public interface TagService {

    List<Tag> listByUserId(Long userId);

    Tag create(Long userId, String name, String color);

    void delete(Long id, Long userId);

    void bind(Long userId, TagBindRequest request);

    void unbind(Long userId, Long tagId, String resourceType, Long resourceId);
}
