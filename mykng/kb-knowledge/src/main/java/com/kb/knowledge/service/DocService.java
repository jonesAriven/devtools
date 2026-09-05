package com.kb.knowledge.service;

import com.marschat.common.page.PageResult;
import com.kb.knowledge.dto.doc.DocCreateRequest;
import com.kb.knowledge.dto.doc.DocMoveRequest;
import com.kb.knowledge.dto.doc.DocUpdateRequest;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.mongo.doc.DocContent;

import java.util.List;

public interface DocService {

    PageResult<Doc> list(Long userId, Long folderId, int page, int size);

    Doc create(Long userId, DocCreateRequest request);

    Doc getById(Long id, Long userId);

    Doc update(Long id, Long userId, DocUpdateRequest request);

    void delete(Long id, Long userId);

    void star(Long id, Long userId);

    void move(Long id, Long userId, DocMoveRequest request);

    List<DocContent> getVersions(Long id, Long userId);
}
