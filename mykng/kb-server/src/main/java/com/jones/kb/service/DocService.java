package com.jones.kb.service;

import com.jones.kb.common.PageResult;
import com.jones.kb.dto.doc.DocCreateRequest;
import com.jones.kb.dto.doc.DocMoveRequest;
import com.jones.kb.dto.doc.DocUpdateRequest;
import com.jones.kb.entity.Doc;

public interface DocService {

    PageResult<Doc> list(Long userId, Long folderId, int page, int size);

    Doc create(Long userId, DocCreateRequest request);

    Doc getById(Long id, Long userId);

    Doc update(Long id, Long userId, DocUpdateRequest request);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);

    void move(Long id, Long userId, DocMoveRequest request);
}
