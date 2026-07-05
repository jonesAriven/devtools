package com.kb.infra.repository;

import com.kb.infra.entity.InfraItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InfraItemRepository extends MongoRepository<InfraItem, String> {

    Optional<InfraItem> findByIdAndDeleted(String id, Integer deleted);

    List<InfraItem> findByTypeAndDeletedOrderBySortOrderAscCreatedAtDesc(String type, Integer deleted);

    List<InfraItem> findByTypeAndCategoryAndDeletedOrderBySortOrderAscCreatedAtDesc(String type, String category, Integer deleted);

    @Query("{ 'type': ?0, 'deleted': ?1, $or: [ { 'name': { $regex: ?2, $options: 'i' } }, { 'description': { $regex: ?2, $options: 'i' } } ] }")
    Page<InfraItem> findByTypeAndKeyword(String type, Integer deleted, String keyword, Pageable pageable);

    @Query("{ 'type': ?0, 'category': ?1, 'deleted': ?2, $or: [ { 'name': { $regex: ?3, $options: 'i' } }, { 'description': { $regex: ?3, $options: 'i' } } ] }")
    Page<InfraItem> findByTypeAndCategoryAndKeyword(String type, String category, Integer deleted, String keyword, Pageable pageable);

    long countByTypeAndDeleted(String type, Integer deleted);

    long countByTypeAndCategoryAndDeleted(String type, String category, Integer deleted);
}
