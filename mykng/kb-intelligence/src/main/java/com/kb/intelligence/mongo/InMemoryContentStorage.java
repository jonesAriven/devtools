package com.kb.intelligence.mongo;

import com.kb.intelligence.mongo.doc.KnContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Profile("!prod")
public class InMemoryContentStorage implements ContentStorage {

    private final Map<Long, KnContent> store = new ConcurrentHashMap<>();

    @Override
    public Optional<KnContent> findByDocId(Long docId) {
        return Optional.ofNullable(store.get(docId));
    }

    @Override
    public void save(KnContent content) {
        store.put(content.getDocId(), content);
        log.debug("InMemoryContentStorage: saved content for docId={}", content.getDocId());
    }

    @Override
    public void deleteByDocId(Long docId) {
        store.remove(docId);
    }
}
