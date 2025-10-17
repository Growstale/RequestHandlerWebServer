package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.WorkCategory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveWorkCategoryRepository extends ReactiveCrudRepository<WorkCategory, Integer> {
    Mono<WorkCategory> findByWorkCategoryName(String workCategoryName);
}