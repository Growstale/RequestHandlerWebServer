package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.NotificationRecipient;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveNotificationRecipientRepository extends ReactiveCrudRepository<NotificationRecipient, Integer> {

    @Query("SELECT * FROM NotificationRecipients WHERE NotificationID = :notificationId")
    Flux<NotificationRecipient> findByNotificationID(Integer notificationId);

    @Query("DELETE FROM NotificationRecipients WHERE NotificationID = :notificationId")
    Mono<Void> deleteByNotificationID(Integer notificationId);
}
