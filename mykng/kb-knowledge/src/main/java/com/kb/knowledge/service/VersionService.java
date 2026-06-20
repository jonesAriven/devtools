package com.kb.knowledge.service;

import com.kb.knowledge.entity.Version;

import java.util.List;

public interface VersionService {

    List<Version> listVersions(String resourceType, Long resourceId);

    Version getVersion(Long id);

    Version rollback(Long id, Long userId);
}
