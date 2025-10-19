package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.repository.ReactiveRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final ReactiveRefreshTokenRepository refreshTokenRepository;

    public TokenCleanupService(ReactiveRefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void cleanupExpiredTokens() {
        log.info("Запуск задачи по очистке истекших refresh-токенов...");
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now())
                .subscribe(
                        count -> log.info("Задача по очистке токенов завершена. Удалено {} токенов.", count),
                        error -> log.error("Ошибка во время очистки истекших токенов.", error)
                );
    }
}