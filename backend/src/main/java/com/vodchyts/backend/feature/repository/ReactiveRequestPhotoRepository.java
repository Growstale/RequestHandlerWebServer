package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.RequestPhoto;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux; // Добавить
import reactor.util.function.Tuple2; // Добавить

import java.util.List;

@Repository
public interface ReactiveRequestPhotoRepository extends ReactiveCrudRepository<RequestPhoto, Integer> {
    Flux<RequestPhoto> findByRequestID(Integer requestID);
    Mono<Long> countByRequestID(Integer requestID);
}