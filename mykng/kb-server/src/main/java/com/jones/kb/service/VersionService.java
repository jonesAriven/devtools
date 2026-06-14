package com.jones.kb.service;

import com.jones.kb.entity.Version;

import java.util.List;

public interface VersionService {

    List<Version> listVersions(String resourceType, Long resourceId);

    Version getVersion(Long id);

    Version rollback(Long id, Long userId);
}
