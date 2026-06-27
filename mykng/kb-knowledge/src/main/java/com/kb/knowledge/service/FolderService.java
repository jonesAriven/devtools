package com.kb.knowledge.service;

import com.kb.knowledge.dto.folder.FolderCreateRequest;
import com.kb.knowledge.dto.folder.FolderMoveRequest;
import com.kb.knowledge.dto.folder.FolderSortRequest;
import com.kb.knowledge.entity.Folder;

import java.util.List;

public interface FolderService {

    List<Folder> getTree(Long spaceId, Long userId);

    Folder getById(Long id, Long userId);

    Folder create(Long userId, FolderCreateRequest request);

    Folder update(Long id, Long userId, String name);

    void delete(Long id, Long userId);

    void move(Long id, Long userId, FolderMoveRequest request);

    void sort(Long id, Long userId, FolderSortRequest request);
}
