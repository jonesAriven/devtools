package com.jones.kb.mongo.repository;

import com.jones.kb.mongo.doc.AiSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AiSummaryRepository extends MongoRepository<AiSummary, String> {

    Optional<AiSummary> findByBizTypeAndBizId(String bizType, Long bizId);

    List<AiSummary> findByBizTypeAndBizIdOrderByCreatedAtDesc(String bizType, Long bizId);
}
