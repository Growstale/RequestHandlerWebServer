package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ReactiveAuditLogRepository extends ReactiveCrudRepository<AuditLog, Integer> {
    Flux<AuditLog> findByUserIDOrderByLogDateDesc(Integer userID, Pageable pageable);
    Flux<AuditLog> findByTableNameOrderByLogDateDesc(String tableName, Pageable pageable);
    Flux<AuditLog> findByActionOrderByLogDateDesc(String action, Pageable pageable);
    Flux<AuditLog> findByLogDateBetweenOrderByLogDateDesc(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    Flux<AuditLog> findAllByOrderByLogDateDesc(Pageable pageable);
    Mono<Long> countByLogDateBefore(LocalDateTime date);
    Flux<AuditLog> findByUserIDAndLogDateBetweenOrderByLogDateDesc(Integer userID, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    @Query("DELETE FROM AuditLog WHERE LogDate < :beforeDate")
    Mono<Integer> deleteByLogDateBefore(LocalDateTime beforeDate);
}

