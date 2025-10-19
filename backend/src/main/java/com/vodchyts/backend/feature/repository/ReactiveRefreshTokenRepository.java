package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.RefreshToken;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ReactiveRefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {
    Mono<RefreshToken> findByTokenHash(String tokenHash);
    Mono<Void> deleteByTokenHash(String tokenHash);
    @Query("DELETE FROM RefreshTokens WHERE ExpiresAt < :now")
    Mono<Integer> deleteExpiredTokens(LocalDateTime now);
}
