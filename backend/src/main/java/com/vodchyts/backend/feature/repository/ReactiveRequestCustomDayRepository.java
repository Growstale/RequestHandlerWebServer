package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.RequestCustomDay;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface ReactiveRequestCustomDayRepository extends ReactiveCrudRepository<RequestCustomDay, Integer> {
    Mono<RequestCustomDay> findByRequestID(Integer requestID);
    Mono<Void> deleteByRequestID(Integer requestID);
    Flux<RequestCustomDay> findByRequestIDIn(List<Integer> requestIds); // <-- ДОБАВЬТЕ ЭТОТ МЕТОД
}