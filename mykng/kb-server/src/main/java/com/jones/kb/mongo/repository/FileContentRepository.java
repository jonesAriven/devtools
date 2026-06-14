package com.jones.kb.mongo.repository;

import com.jones.kb.mongo.doc.FileContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileContentRepository extends MongoRepository<FileContent, String> {

    Optional<FileContent> findByFileIdAndIsCurrentTrue(Long fileId);

    List<FileContent> findByFileIdOrderByVersionDesc(Long fileId);

    List<FileContent> findByUserIdAndIsCurrentTrue(Long userId);
}
