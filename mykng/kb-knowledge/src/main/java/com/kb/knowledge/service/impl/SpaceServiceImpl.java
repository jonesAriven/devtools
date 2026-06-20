package com.kb.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kb.common.exception.BusinessException;
import com.kb.knowledge.dto.space.SpaceCreateRequest;
import com.kb.knowledge.dto.space.SpaceUpdateRequest;
import com.kb.knowledge.entity.Space;
import com.kb.knowledge.mapper.SpaceMapper;
import com.kb.knowledge.service.EventPublisher;
import com.kb.knowledge.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;
    private final EventPublisher eventPublisher;

    @Override
    public List<Space> listByUserId(Long userId) {
        return spaceMapper.selectList(
                new LambdaQueryWrapper<Space>()
                        .eq(Space::getUserId, userId)
                        .orderByDesc(Space::getCreatedAt));
    }

    @Override
    public Space create(Long userId, SpaceCreateRequest request) {
        Space space = new Space();
        space.setUserId(userId);
        space.setName(request.getName());
        space.setType(request.getType() != null ? request.getType() : "private");
        space.setDescription(request.getDescription());
        space.setStatus(1);
        spaceMapper.insert(space);

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "CREATE", "space", space.getId(),
                "创建空间: " + request.getName());
        return space;
    }

    @Override
    public Space update(Long id, Long userId, SpaceUpdateRequest request) {
        Space space = getAndCheckOwner(id, userId);
        if (request.getName() != null) space.setName(request.getName());
        if (request.getType() != null) space.setType(request.getType());
        if (request.getDescription() != null) space.setDescription(request.getDescription());
        if (request.getStatus() != null) space.setStatus(request.getStatus());
        spaceMapper.updateById(space);
        return space;
    }

    @Override
    public void delete(Long id, Long userId) {
        Space space = getAndCheckOwner(id, userId);
        spaceMapper.deleteById(space.getId());

        // 发布操作事件
        eventPublisher.publishKnowledgeEvent(userId, "DELETE", "space", id,
                "删除空间: " + space.getName());
    }

    private Space getAndCheckOwner(Long id, Long userId) {
        Space space = spaceMapper.selectById(id);
        if (space == null) {
            throw new BusinessException("空间不存在");
        }
        if (!space.getUserId().equals(userId)) {
            throw new BusinessException("无权限操作此空间");
        }
        return space;
    }
}
