package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.repository.ReactiveRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final ReactiveRefreshTokenRepository refreshTokenRepository;
    private final LoggingService loggingService;

    public TokenCleanupService(ReactiveRefreshTokenRepository refreshTokenRepository, LoggingService loggingService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.loggingService = loggingService;
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void cleanupExpiredTokens() {
        log.info("Запуск задачи по очистке истекших refresh-токенов...");
        refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now())
                .subscribe(
                        count -> {
                            log.info("Задача по очистке токенов завершена. Удалено {} токенов.", count);
                            if (count > 0) {
                                loggingService.logInfo("TokenCleanupService", "Очистка истекших токенов завершена. Удалено: " + count,
                                        null, "SYSTEM", "127.0.0.1", "Quartz Scheduler", "cron", "JOB").subscribe();
                            }
                        },
                        error -> log.error("Ошибка во время очистки истекших токенов.", error)
                );
    }
}
