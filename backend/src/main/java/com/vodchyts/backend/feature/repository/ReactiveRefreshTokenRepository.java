package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ReactiveRefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {
    Mono<RefreshToken> findByTokenHash(String tokenHash);
    Mono<Void> deleteByTokenHash(String tokenHash);
}
