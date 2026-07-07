package com.kb.portal.service;

import com.kb.common.page.PageResult;
import com.kb.portal.dto.PortalSystemRequest;
import com.kb.portal.dto.SystemCredentials;
import com.kb.portal.entity.PortalSystem;

import java.util.List;

public interface PortalSystemService {

    PageResult<PortalSystem> list(String keyword, String category, Integer status, Boolean hasCredentials, Boolean hasUrl, int page, int size);

    List<PortalSystem> listByCategory(String category);

    List<PortalSystem> listAllEnabled();

    PortalSystem getById(Long id);

    SystemCredentials getCredentials(Long id);

    PortalSystem create(PortalSystemRequest request);

    PortalSystem update(Long id, PortalSystemRequest request);

    void delete(Long id);
}
