package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.WebNotification;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveWebNotificationRepository extends ReactiveCrudRepository<WebNotification, Integer> {
    Flux<WebNotification> findByUserIDAndIsReadFalseOrderByCreatedAtDesc(Integer userID);
    Mono<Void> deleteByUserID(Integer userID);
}
