package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.MessageTemplate;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveMessageTemplateRepository extends ReactiveCrudRepository<MessageTemplate, Integer> {
    Mono<MessageTemplate> findByTitle(String title);
}