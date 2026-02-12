package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Notification;
import com.vodchyts.backend.feature.entity.NotificationRecipient;
import com.vodchyts.backend.feature.repository.ReactiveNotificationRecipientRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopContractorChatRepository;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
public class NotificationSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final Scheduler scheduler;
    private final ReactiveNotificationRecipientRepository recipientRepository;

    public NotificationSchedulerService(Scheduler scheduler, 
                                      ReactiveNotificationRecipientRepository recipientRepository) {
        this.scheduler = scheduler;
        this.recipientRepository = recipientRepository;
    }

    public void scheduleNotification(Notification notification) {
        try {
            JobDetail jobDetail = JobBuilder.newJob(NotificationJob.class)
                    .withIdentity("notification-" + notification.getNotificationID(), "notifications")
                    .usingJobData("notificationId", notification.getNotificationID())
                    .usingJobData("title", notification.getTitle())
                    .usingJobData("message", notification.getMessage() != null ? notification.getMessage() : "")
                    .build();

            String quartzCron = convertToQuartzCron(notification.getCronExpression());

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + notification.getNotificationID(), "notifications")
                    .withSchedule(CronScheduleBuilder.cronSchedule(quartzCron))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            logger.info("Запланировано уведомление ID: {}", notification.getNotificationID());
        } catch (SchedulerException e) {
            logger.error("Ошибка планирования: {}", e.getMessage());
        }
    }

    public void unscheduleNotification(Integer notificationId) {
        try {
            JobKey jobKey = JobKey.jobKey("notification-" + notificationId, "notifications");
            scheduler.deleteJob(jobKey);
        } catch (SchedulerException e) {
            logger.error("Error unscheduling", e);
        }
    }

    public void rescheduleNotification(Notification notification) {
        unscheduleNotification(notification.getNotificationID());
        if (Boolean.TRUE.equals(notification.getIsActive())) {
            scheduleNotification(notification);
        }
    }

    private String convertToQuartzCron(String cronExpression) {
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Ожидалось 5 частей cron выражения, получено: " + parts.length);
        }

        String minute = parts[0];
        String hour = parts[1];
        String dayOfMonth = parts[2];
        String month = parts[3];
        String dayOfWeek = parts[4];

        boolean isDayOfMonthSpecified = !"*".equals(dayOfMonth);
        boolean isDayOfWeekSpecified = !"*".equals(dayOfWeek);

        if (isDayOfMonthSpecified && isDayOfWeekSpecified) {
            dayOfWeek = "?";
        } else if (isDayOfWeekSpecified) {
            dayOfMonth = "?";
        } else if (isDayOfMonthSpecified) {
            dayOfWeek = "?";
        } else {
            dayOfWeek = "?";
        }

        if (!"?".equals(dayOfWeek)) {
            dayOfWeek = convertDayOfWeekToQuartz(dayOfWeek);
        }

        return String.format("0 %s %s %s %s %s", minute, hour, dayOfMonth, month, dayOfWeek);
    }

    private String convertDayOfWeekToQuartz(String dayOfWeek) {
        return switch (dayOfWeek.toUpperCase()) {
            case "0", "7", "SUN" -> "1";
            case "1", "MON" -> "2";
            case "2", "TUE" -> "3";
            case "3", "WED" -> "4";
            case "4", "THU" -> "5";
            case "5", "FRI" -> "6";
            case "6", "SAT" -> "7";
            default -> dayOfWeek;
        };
    }

    @Component
    public static class NotificationJob implements Job {

        private ReactiveNotificationRecipientRepository recipientRepository;
        private ReactiveShopContractorChatRepository chatRepository;
        private TelegramNotificationService telegramService;

        @org.springframework.beans.factory.annotation.Autowired
        public void setRecipientRepository(ReactiveNotificationRecipientRepository recipientRepository) {
            this.recipientRepository = recipientRepository;
        }

        @org.springframework.beans.factory.annotation.Autowired
        public void setChatRepository(ReactiveShopContractorChatRepository chatRepository) {
            this.chatRepository = chatRepository;
        }

        @org.springframework.beans.factory.annotation.Autowired
        public void setTelegramService(TelegramNotificationService telegramService) {
            this.telegramService = telegramService;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            DayOfWeek today = LocalDate.now().getDayOfWeek();
            if (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY) {
                logger.info("Сегодня выходной, рассылка уведомлений пропущена.");
                return;
            }

            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            Integer notificationId = dataMap.getInt("notificationId");
            String rawTitle = dataMap.getString("title");   // <-- Получаем сырой заголовок
            String rawMessage = dataMap.getString("message"); // <-- Получаем сырое сообщение

            String safeTitle = telegramService.escapeMarkdown(rawTitle);
            String safeMessage = telegramService.escapeMarkdown(rawMessage);

            String fullMessage = "*" + safeTitle + "*\n\n" + safeMessage;

            logger.info("Начало рассылки уведомления ID={}", notificationId);

            recipientRepository.findByNotificationID(notificationId)
                    .flatMap(recipient -> chatRepository.findById(recipient.getShopContractorChatID()))
                    .flatMap(chat -> {
                        return telegramService.sendNotification(chat.getTelegramID(), fullMessage);
                    })
                    .subscribe();
        }
    }
}
