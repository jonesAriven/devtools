package com.kb.infra.repository;

import com.kb.infra.entity.InfraHealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InfraHealthLogRepository extends MongoRepository<InfraHealthLog, String> {

    Page<InfraHealthLog> findByServiceIdOrderByCheckedAtDesc(String serviceId, Pageable pageable);

    List<InfraHealthLog> findTop10ByServiceIdOrderByCheckedAtDesc(String serviceId);

    List<InfraHealthLog> findByServiceIdAndCheckedAtAfterOrderByCheckedAtDesc(String serviceId, LocalDateTime after);

    long countByServiceIdAndStatus(String serviceId, String status);
}
