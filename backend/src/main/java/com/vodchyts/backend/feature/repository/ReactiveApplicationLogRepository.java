package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.ApplicationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ReactiveApplicationLogRepository extends ReactiveCrudRepository<ApplicationLog, Long> {
    Flux<ApplicationLog> findByLogLevelOrderByLogDateDesc(String logLevel, Pageable pageable);
    Flux<ApplicationLog> findByLogDateBetweenOrderByLogDateDesc(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    Flux<ApplicationLog> findByUserIDOrderByLogDateDesc(Integer userID, Pageable pageable);
    Flux<ApplicationLog> findByLoggerNameContainingIgnoreCaseOrderByLogDateDesc(String loggerName, Pageable pageable);
    Flux<ApplicationLog> findAllByOrderByLogDateDesc(Pageable pageable);
    Mono<Long> countByLogDateBefore(LocalDateTime date);
    Mono<Long> countByLogLevelAndLogDateBetween(String logLevel, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("DELETE FROM ApplicationLog WHERE LogDate < :beforeDate")
    Mono<Integer> deleteByLogDateBefore(LocalDateTime beforeDate);
}

