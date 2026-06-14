package com.jones.kb.service;

import com.jones.kb.dto.folder.FolderCreateRequest;
import com.jones.kb.dto.folder.FolderMoveRequest;
import com.jones.kb.dto.folder.FolderSortRequest;
import com.jones.kb.entity.Folder;

import java.util.List;

public interface FolderService {

    List<Folder> getTree(Long spaceId, Long userId);

    Folder create(Long userId, FolderCreateRequest request);

    Folder update(Long id, Long userId, String name);

    void delete(Long id, Long userId);

    void move(Long id, Long userId, FolderMoveRequest request);

    void sort(Long id, Long userId, FolderSortRequest request);
}
