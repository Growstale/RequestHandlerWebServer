package com.vodchyts.backend.feature.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupService.class);

    private final LoggingService loggingService;
    private final AuditService auditService;

    @Value("${logging.retention.days:90}")
    private int logRetentionDays = 90;

    @Value("${audit.retention.days:365}")
    private int auditRetentionDays = 365;

    public LogCleanupService(LoggingService loggingService, AuditService auditService) {
        this.loggingService = loggingService;
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 0 2 * * *") // Каждый день в 2:00 ночи
    public void cleanupOldLogs() {
        log.info("Запуск задачи по очистке старых логов...");
        
        LocalDateTime logCutoffDate = LocalDateTime.now().minusDays(logRetentionDays);
        LocalDateTime auditCutoffDate = LocalDateTime.now().minusDays(auditRetentionDays);

        loggingService.deleteOldLogs(logCutoffDate)
                .subscribe(
                        count -> log.info("Очистка логов приложения завершена. Удалено {} записей старше {} дней.", 
                                count, logRetentionDays),
                        error -> log.error("Ошибка во время очистки логов приложения.", error)
                );

        auditService.deleteOldAuditLogs(auditCutoffDate)
                .subscribe(
                        count -> log.info("Очистка записей аудита завершена. Удалено {} записей старше {} дней.", 
                                count, auditRetentionDays),
                        error -> log.error("Ошибка во время очистки записей аудита.", error)
                );
    }
}

