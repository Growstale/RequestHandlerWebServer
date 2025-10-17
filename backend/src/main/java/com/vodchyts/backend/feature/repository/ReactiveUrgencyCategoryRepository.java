package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.UrgencyCategory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveUrgencyCategoryRepository extends ReactiveCrudRepository<UrgencyCategory, Integer> {
    Mono<UrgencyCategory> findByUrgencyName(String urgencyName);
}