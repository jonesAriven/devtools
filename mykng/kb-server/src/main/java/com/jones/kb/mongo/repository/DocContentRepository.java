package com.jones.kb.mongo.repository;

import com.jones.kb.mongo.doc.DocContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocContentRepository extends MongoRepository<DocContent, String> {

    Optional<DocContent> findByDocIdAndIsCurrentTrue(Long docId);

    List<DocContent> findByDocIdOrderByVersionDesc(Long docId);

    List<DocContent> findByUserIdAndIsCurrentTrue(Long userId);
}
