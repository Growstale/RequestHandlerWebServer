package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.MessageRecipient;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface ReactiveMessageRecipientRepository extends ReactiveCrudRepository<MessageRecipient, Integer> {
    Flux<MessageRecipient> findByMessageID(Integer messageId);
    Mono<Void> deleteByMessageID(Integer messageId);

    Flux<MessageRecipient> findByMessageIDIn(List<Integer> messageIds);
}