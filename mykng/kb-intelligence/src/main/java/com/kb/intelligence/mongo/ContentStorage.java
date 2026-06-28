package com.kb.intelligence.mongo;

import com.kb.intelligence.mongo.doc.KnContent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public interface ContentStorage {
    Optional<KnContent> findByDocId(Long docId);
    void save(KnContent content);
    void deleteByDocId(Long docId);
}
