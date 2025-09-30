package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveUserRepository extends ReactiveCrudRepository<User, Integer> {
    Mono<User> findByLogin(String login);
    Mono<Boolean> existsByLogin(String login);
}
