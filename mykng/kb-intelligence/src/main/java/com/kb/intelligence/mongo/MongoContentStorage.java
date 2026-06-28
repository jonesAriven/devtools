package com.kb.intelligence.mongo;

import com.kb.intelligence.mongo.doc.KnContent;
import com.kb.intelligence.mongo.repository.KnContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Primary
@Profile("prod")
@RequiredArgsConstructor
public class MongoContentStorage implements ContentStorage {

    private final KnContentRepository repository;

    @Override
    public Optional<KnContent> findByDocId(Long docId) {
        return repository.findByDocId(docId);
    }

    @Override
    public void save(KnContent content) {
        repository.save(content);
    }

    @Override
    public void deleteByDocId(Long docId) {
        repository.deleteByDocId(docId);
    }
}
