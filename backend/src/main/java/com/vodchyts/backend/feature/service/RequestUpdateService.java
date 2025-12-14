package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Request;
import com.vodchyts.backend.feature.entity.RequestCustomDay;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.repository.ReactiveRequestCustomDayRepository;
import com.vodchyts.backend.feature.repository.ReactiveRequestRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopContractorChatRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

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
    }

    // БЫЛО: @Scheduled(cron = "0 0 * * * *")
// СТАЛО (для теста):
    @Scheduled(fixedDelay = 30000) // Каждые 30 сек
    public void checkNewOverduesJob() {
        updateOverdueStatus(true).subscribe();
    }

    // Публичный метод для принудительного обновления (например, при старте), но без уведомлений
    public Mono<Long> updateOverdueStatus() {
        return updateOverdueStatus(false);
    }

    private Mono<Long> updateOverdueStatus(boolean sendNotification) {
        log.info("Проверка статусов просрочки...");

        Flux<Request> activeRequests = template.select(query(where("Status").is("In work")), Request.class);
        Mono<Map<Integer, UrgencyCategory>> urgencyMapMono = urgencyCategoryRepository.findAll().collectMap(UrgencyCategory::getUrgencyID);
        Mono<Map<Integer, RequestCustomDay>> customDaysMapMono = customDayRepository.findAll().collectMap(RequestCustomDay::getRequestID);

        return Mono.zip(urgencyMapMono, customDaysMapMono)
                .flatMapMany(tuple -> {
                    Map<Integer, UrgencyCategory> urgencyMap = tuple.getT1();
                    Map<Integer, RequestCustomDay> customDaysMap = tuple.getT2();

                    return activeRequests.flatMap(request -> {
                        UrgencyCategory urgency = urgencyMap.get(request.getUrgencyID());
                        if (urgency == null) return Mono.empty();

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDaysMap.getOrDefault(request.getRequestID(), new RequestCustomDay()).getDays()
                                : urgency.getDefaultDays();

                        if (daysForTask == null) return Mono.empty();

                        LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                        boolean isNowOverdue = LocalDateTime.now().isAfter(deadline);

                        // Логика: Если статус меняется с False на True -> это НОВАЯ просрочка
                        boolean isTransitionToOverdue = isNowOverdue && (request.getIsOverdue() == null || !request.getIsOverdue());

                        if (isNowOverdue != (request.getIsOverdue() != null && request.getIsOverdue())) {
                            request.setIsOverdue(isNowOverdue);

                            // Сохраняем в БД
                            return requestRepository.save(request)
                                    .flatMap(savedReq -> {
                                        // Если это переход в просрочку И нужно слать уведомления И сегодня не выходной
                                        if (isTransitionToOverdue && sendNotification && !isWeekend()) {
                                            return sendOverdueAlert(savedReq, 1); // 1й день просрочки (свежая)
                                        }
                                        return Mono.just(savedReq);
                                    });
                        }
                        return Mono.empty();
                    });
                })
                .count()
                .doOnSuccess(c -> log.info("Обновлено заявок: {}", c));
    }


    // 2. ЕЖЕДНЕВНОЕ НАПОМИНАНИЕ (Только по будням в 10:00)
    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void sendDailyReminders() {
        log.info("Запуск ежедневной рассылки напоминаний...");

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

                        // Если просрочка < 1 дня (сегодня), мы уже отправили уведомление в hourly джобе (checkNewOverduesJob).
                        // Напоминаем только о тех, где прошло больше 1 дня.
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

        // 1. Подготавливаем описание и ЭКРАНИРУЕМ его спецсимволы
        String rawDescription = request.getDescription() != null
                ? request.getDescription().substring(0, Math.min(request.getDescription().length(), 50)) + "..."
                : "";
        // Используем метод из notificationService для экранирования пользовательского текста
        String safeDescription = notificationService.escapeMarkdown(rawDescription);

        // 2. Формируем сообщение.
        // ВАЖНО: В MarkdownV2 символы #, ., ! должны быть экранированы как \#, \., \!
        // В Java строках обратный слэш пишется как \\
        String message = String.format(
                "%s *ЗАЯВКА \\#%d ПРОСРОЧЕНА*\n\n" +        // \# вместо #
                        "Срок истек: *%d дн\\. назад*\n" +          // \. вместо .
                        "Описание: %s",                   // \! вместо !
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