package com.jones.kb.service;

import com.jones.kb.common.PageResult;
import com.jones.kb.dto.space.SpaceCreateRequest;
import com.jones.kb.dto.space.SpaceUpdateRequest;
import com.jones.kb.entity.Space;

import java.util.List;

public interface SpaceService {

    List<Space> listByUserId(Long userId);

    Space create(Long userId, SpaceCreateRequest request);

    Space update(Long id, Long userId, SpaceUpdateRequest request);

    void delete(Long id, Long userId);
}
