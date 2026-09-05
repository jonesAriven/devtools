package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marschat.common.exception.BusinessException;
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
import java.util.Map;
import java.util.HashMap;

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
    public List<Map<String, Object>> getTagStats(Long userId) {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().eq(Tag::getUserId, userId));
        if (tags.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> tagIds = tags.stream().map(Tag::getId).toList();
        List<ResourceTag> rts = resourceTagMapper.selectList(
                new LambdaQueryWrapper<ResourceTag>().in(ResourceTag::getTagId, tagIds));
        Map<Long, Integer> countMap = new HashMap<>();
        for (ResourceTag rt : rts) {
            countMap.merge(rt.getTagId(), 1, Integer::sum);
        }
        return tags.stream().map(tag -> {
            Map<String, Object> stat = new HashMap<>();
            stat.put("id", tag.getId());
            stat.put("name", tag.getName());
            stat.put("color", tag.getColor());
            stat.put("count", countMap.getOrDefault(tag.getId(), 0));
            return stat;
        }).toList();
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
    public Tag update(Long id, Long userId, String name, String color) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw new BusinessException("标签不存在");
        }
        if (name != null && !name.equals(tag.getName())) {
            Tag exist = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>()
                            .eq(Tag::getUserId, userId)
                            .eq(Tag::getName, name)
                            .ne(Tag::getId, id));
            if (exist != null) {
                throw new BusinessException("标签名称已存在");
            }
            tag.setName(name);
        }
        if (color != null) {
            tag.setColor(color);
        }
        tagMapper.updateById(tag);

        eventPublisher.publishKnowledgeEvent(userId, "UPDATE", "tag", tag.getId(),
                "更新标签: " + tag.getName());
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

    @Override
    public List<Tag> getTagsByResource(Long userId, Long resourceId, String resourceType) {
        List<ResourceTag> rts = resourceTagMapper.selectList(
                new LambdaQueryWrapper<ResourceTag>()
                        .eq(ResourceTag::getResourceId, resourceId)
                        .eq(ResourceTag::getResourceType, resourceType));
        if (rts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> tagIds = rts.stream().map(ResourceTag::getTagId).toList();
        return tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .in(Tag::getId, tagIds)
                        .eq(Tag::getUserId, userId));
    }
}
