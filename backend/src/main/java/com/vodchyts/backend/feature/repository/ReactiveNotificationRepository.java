package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveNotificationRepository extends ReactiveCrudRepository<Notification, Integer> {

    @Query("SELECT * FROM Notifications WHERE IsActive = :isActive ORDER BY NotificationID")
    Flux<Notification> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT COUNT(*) FROM Notifications WHERE IsActive = :isActive")
    Mono<Long> countByIsActive(Boolean isActive);

    @Query("SELECT * FROM Notifications WHERE IsActive = 1")
    Flux<Notification> findActiveNotifications();

    Mono<Boolean> existsByTitle(String title);
}
