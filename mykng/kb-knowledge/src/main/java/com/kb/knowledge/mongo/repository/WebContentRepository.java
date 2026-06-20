package com.kb.knowledge.mongo.repository;

import com.kb.knowledge.mongo.doc.WebContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WebContentRepository extends MongoRepository<WebContent, String> {

    Optional<WebContent> findByWebIdAndIsCurrentTrue(Long webId);

    List<WebContent> findByWebIdOrderByVersionDesc(Long webId);

    List<WebContent> findByUserIdAndIsCurrentTrue(Long userId);
}
