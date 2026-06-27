package com.kb.knowledge.service;

import com.kb.knowledge.dto.space.SpaceCreateRequest;
import com.kb.knowledge.dto.space.SpaceUpdateRequest;
import com.kb.knowledge.entity.Space;

import java.util.List;

public interface SpaceService {

    List<Space> listByUserId(Long userId);

    Space getById(Long id, Long userId);

    Space create(Long userId, SpaceCreateRequest request);

    Space update(Long id, Long userId, SpaceUpdateRequest request);

    void delete(Long id, Long userId);
}
