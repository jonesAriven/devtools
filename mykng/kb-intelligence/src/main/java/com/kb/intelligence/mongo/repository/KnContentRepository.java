package com.kb.intelligence.mongo.repository;

import com.kb.intelligence.mongo.doc.KnContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KnContentRepository extends MongoRepository<KnContent, String> {
    Optional<KnContent> findByDocId(Long docId);
    void deleteByDocId(Long docId);
}
