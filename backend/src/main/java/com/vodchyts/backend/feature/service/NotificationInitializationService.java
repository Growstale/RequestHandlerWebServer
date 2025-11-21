package com.vodchyts.backend.feature.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class NotificationInitializationService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(NotificationInitializationService.class);

    private final NotificationService notificationService;
    private final NotificationSchedulerService schedulerService;

    public NotificationInitializationService(NotificationService notificationService,
                                           NotificationSchedulerService schedulerService) {
        this.notificationService = notificationService;
        this.schedulerService = schedulerService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Инициализация планировщика уведомлений...");
        
        notificationService.getActiveNotifications()
                .doOnNext(notification -> {
                    logger.info("Планирование активного уведомления: ID={}, Title={}", 
                               notification.getNotificationID(), notification.getTitle());
                    schedulerService.scheduleNotification(notification);
                })
                .doOnComplete(() -> logger.info("Инициализация планировщика уведомлений завершена"))
                .subscribe();
    }
}
