package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Request;
import com.vodchyts.backend.feature.entity.RequestCustomDay;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.repository.ReactiveRequestCustomDayRepository;
import com.vodchyts.backend.feature.repository.ReactiveRequestRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopContractorChatRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

@Service
public class RequestUpdateService {

    private static final Logger log = LoggerFactory.getLogger(RequestUpdateService.class);

    private final R2dbcEntityTemplate template;
    private final ReactiveRequestRepository requestRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;
    private final ReactiveRequestCustomDayRepository customDayRepository;
    private final ReactiveShopContractorChatRepository chatRepository;
    private final TelegramNotificationService notificationService;

    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> overdueCheckTask;
    private ScheduledFuture<?> dailyReminderTask;

    private long currentCheckInterval = 30000;
    private String currentReminderCron = "0 0 10 * * MON-FRI";

    public RequestUpdateService(R2dbcEntityTemplate template,
                                ReactiveRequestRepository requestRepository,
                                ReactiveUrgencyCategoryRepository urgencyCategoryRepository,
                                ReactiveRequestCustomDayRepository customDayRepository,
                                ReactiveShopContractorChatRepository chatRepository,
                                TelegramNotificationService notificationService) {
        this.template = template;
        this.requestRepository = requestRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.customDayRepository = customDayRepository;
        this.chatRepository = chatRepository;
        this.notificationService = notificationService;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("DynamicScheduler-");
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    @PostConstruct
    public void init() {
        startOverdueCheckTask(currentCheckInterval);
        startDailyReminderTask(currentReminderCron);
    }


    public void restartOverdueCheck(long intervalMillis) {
        if (overdueCheckTask != null) {
            overdueCheckTask.cancel(false);
        }
        this.currentCheckInterval = intervalMillis;
        startOverdueCheckTask(intervalMillis);
        log.info("Интервал проверки просрочек изменен на {} мс", intervalMillis);
    }

    public void restartDailyReminder(String cronExpression) {
        if (dailyReminderTask != null) {
            dailyReminderTask.cancel(false);
        }
        this.currentReminderCron = cronExpression;
        startDailyReminderTask(cronExpression);
        log.info("Расписание напоминаний изменено на '{}'", cronExpression);
    }

    public Map<String, Object> getCurrentConfig() {
        return Map.of(
                "checkInterval", currentCheckInterval,
                "reminderCron", currentReminderCron
        );
    }

    private void startOverdueCheckTask(long interval) {
        overdueCheckTask = taskScheduler.scheduleWithFixedDelay(
                () -> updateOverdueStatus(true).subscribe(),
                Duration.ofMillis(interval)
        );
    }

    private void startDailyReminderTask(String cron) {
        try {
            dailyReminderTask = taskScheduler.schedule(
                    this::sendDailyReminders,
                    new CronTrigger(cron)
            );
        } catch (Exception e) {
            log.error("Ошибка в CRON выражении: {}", cron, e);
        }
    }


    public Mono<Void> updateRequestDate(Integer requestId, LocalDateTime newDate) {
        String sql = "UPDATE Requests SET CreatedAt = :newDate WHERE RequestID = :requestId";

        return template.getDatabaseClient().sql(sql)
                .bind("newDate", newDate)
                .bind("requestId", requestId)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    return updateOverdueStatus(true);
                })
                .then();
    }

    public Mono<Long> updateOverdueStatus() {
        return updateOverdueStatus(false);
    }

    public Mono<Long> forceCheckNow() {
        return updateOverdueStatus(true);
    }

    public void forceRemindNow() {
        sendDailyReminders();
    }

    private Mono<Long> updateOverdueStatus(boolean sendNotification) {
        log.info("Проверка статусов просрочки...");

        Flux<Request> requestsToCheck = template.select(
                query(where("Status").in("In work", "Done")),
                Request.class
        );

        Mono<Map<Integer, UrgencyCategory>> urgencyMapMono = urgencyCategoryRepository.findAll().collectMap(UrgencyCategory::getUrgencyID);
        Mono<Map<Integer, RequestCustomDay>> customDaysMapMono = customDayRepository.findAll().collectMap(RequestCustomDay::getRequestID);

        return Mono.zip(urgencyMapMono, customDaysMapMono)
                .flatMapMany(tuple -> {
                    Map<Integer, UrgencyCategory> urgencyMap = tuple.getT1();
                    Map<Integer, RequestCustomDay> customDaysMap = tuple.getT2();

                    return requestsToCheck.flatMap(request -> {
                        UrgencyCategory urgency = urgencyMap.get(request.getUrgencyID());
                        if (urgency == null) return Mono.empty();

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDaysMap.getOrDefault(request.getRequestID(), new RequestCustomDay()).getDays()
                                : urgency.getDefaultDays();

                        if (daysForTask == null) return Mono.empty();

                        LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                        boolean isNowOverdue = LocalDateTime.now().isAfter(deadline);

                        boolean isTransitionToOverdue = isNowOverdue && (request.getIsOverdue() == null || !request.getIsOverdue());

                        if (isNowOverdue != (request.getIsOverdue() != null && request.getIsOverdue())) {
                            request.setIsOverdue(isNowOverdue);

                            return requestRepository.save(request)
                                    .flatMap(savedReq -> {
                                        if ("In work".equalsIgnoreCase(savedReq.getStatus()) &&
                                                isTransitionToOverdue &&
                                                sendNotification &&
                                                !isWeekend()) {

                                            long realDaysOverdue = Duration.between(deadline, LocalDateTime.now()).toDays();
                                            long daysReported = Math.max(1, realDaysOverdue);
                                            return sendOverdueAlert(savedReq, daysReported);
                                        }
                                        return Mono.just(savedReq);
                                    });
                        }
                        return Mono.empty();
                    });
                })
                .count()
                .doOnSuccess(c -> log.info("Обновлено заявок (просрочка): {}", c));
    }

    public void sendDailyReminders() {
        log.info("Запуск рассылки напоминаний...");

        Flux<Request> overdueRequests = template.select(query(where("Status").is("In work").and("IsOverdue").is(true)), Request.class);
        Mono<Map<Integer, UrgencyCategory>> urgencyMapMono = urgencyCategoryRepository.findAll().collectMap(UrgencyCategory::getUrgencyID);
        Mono<Map<Integer, RequestCustomDay>> customDaysMapMono = customDayRepository.findAll().collectMap(RequestCustomDay::getRequestID);

        Mono.zip(urgencyMapMono, customDaysMapMono)
                .flatMapMany(tuple -> {
                    Map<Integer, UrgencyCategory> urgencyMap = tuple.getT1();
                    Map<Integer, RequestCustomDay> customDaysMap = tuple.getT2();

                    return overdueRequests.flatMap(request -> {
                        UrgencyCategory urgency = urgencyMap.get(request.getUrgencyID());
                        if (urgency == null) return Mono.empty();

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDaysMap.getOrDefault(request.getRequestID(), new RequestCustomDay()).getDays()
                                : urgency.getDefaultDays();

                        if (daysForTask == null) return Mono.empty();

                        LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                        long daysOverdue = Duration.between(deadline, LocalDateTime.now()).toDays();

                        if (daysOverdue >= 1) {
                            return sendOverdueAlert(request, daysOverdue);
                        }
                        return Mono.empty();
                    });
                })
                .subscribe();
    }

    private Mono<Void> sendOverdueAlert(Request request, long daysOverdue) {
        String icon = daysOverdue == 1 ? "⚠️" : "🔥";
        String desc = request.getDescription();
        String rawDescription = "";

        if (desc != null) {
            if (desc.length() > 50) {
                rawDescription = desc.substring(0, 50) + "...";
            } else {
                rawDescription = desc;
            }
        }

        String safeDescription = notificationService.escapeMarkdown(rawDescription);

        String message = String.format(
                "%s *ЗАЯВКА \\#%d ПРОСРОЧЕНА*\n\n" +
                        "Срок истек: *%d дн\\. назад*\n" +
                        "Описание: %s",
                icon, request.getRequestID(), daysOverdue,
                safeDescription
        );

        return chatRepository.findTelegramIdByRequestId(request.getRequestID())
                .flatMap(chatId -> notificationService.sendNotification(chatId, message))
                .then();
    }

    private boolean isWeekend() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY;
    }
}