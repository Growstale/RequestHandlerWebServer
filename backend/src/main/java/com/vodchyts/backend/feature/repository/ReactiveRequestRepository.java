package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.Request;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveRequestRepository extends ReactiveCrudRepository<Request, Integer> {
    Mono<Long> countByWorkCategoryID(Integer categoryId);
}