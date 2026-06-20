package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.tag.TagBindRequest;
import com.kb.knowledge.entity.ResourceTag;
import com.kb.knowledge.entity.Tag;
import com.kb.knowledge.mapper.ResourceTagMapper;
import com.kb.knowledge.mapper.TagMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final EventPublisher eventPublisher;

    @Override
    public List<Tag> listByUserId(Long userId) {
        return tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, userId)
                        .orderByDesc(Tag::getCreatedAt));
    }

    @Override
    public Tag create(Long userId, String name, String color) {
        Tag exist = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, userId)
                        .eq(Tag::getName, name));
        if (exist != null) {
            throw new BusinessException("标签已存在");
        }

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        tagMapper.insert(tag);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "CREATE", "tag", tag.getId(),
                "创建标签: " + name);
        return tag;
    }

    @Override
    public void delete(Long id, Long userId) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException("标签不存在");
        }
        tagMapper.deleteById(id);
        resourceTagMapper.delete(
                new LambdaQueryWrapper<ResourceTag>().eq(ResourceTag::getTagId, id));

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "tag", id,
                "删除标签: " + tag.getName());
    }

    @Override
    @Transactional
    public void bind(Long userId, TagBindRequest request) {
        Tag tag = tagMapper.selectById(request.getTagId());
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException("标签不存在");
        }

        ResourceTag exist = resourceTagMapper.selectOne(
                new LambdaQueryWrapper<ResourceTag>()
                        .eq(ResourceTag::getTagId, request.getTagId())
                        .eq(ResourceTag::getResourceType, request.getResourceType())
                        .eq(ResourceTag::getResourceId, request.getResourceId()));
        if (exist != null) {
            return;
        }

        ResourceTag resourceTag = new ResourceTag();
        resourceTag.setTagId(request.getTagId());
        resourceTag.setResourceType(request.getResourceType());
        resourceTag.setResourceId(request.getResourceId());
        resourceTagMapper.insert(resourceTag);
    }

    @Override
    public void unbind(Long userId, Long tagId, String resourceType, Long resourceId) {
        resourceTagMapper.delete(
                new LambdaQueryWrapper<ResourceTag>()
                        .eq(ResourceTag::getTagId, tagId)
                        .eq(ResourceTag::getResourceType, resourceType)
                        .eq(ResourceTag::getResourceId, resourceId));
    }
}
