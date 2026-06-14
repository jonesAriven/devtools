package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.dto.space.SpaceCreateRequest;
import com.jones.kb.dto.space.SpaceUpdateRequest;
import com.jones.kb.entity.Space;
import com.jones.kb.mapper.SpaceMapper;
import com.jones.kb.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;

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
