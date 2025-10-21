package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Request;
import com.vodchyts.backend.feature.entity.RequestCustomDay;
import com.vodchyts.backend.feature.entity.UrgencyCategory;
import com.vodchyts.backend.feature.repository.ReactiveRequestCustomDayRepository;
import com.vodchyts.backend.feature.repository.ReactiveRequestRepository;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    public RequestUpdateService(R2dbcEntityTemplate template, ReactiveRequestRepository requestRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository, ReactiveRequestCustomDayRepository customDayRepository) {
        this.template = template;
        this.requestRepository = requestRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.customDayRepository = customDayRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void updateOverdueStatus() {
        log.info("Запуск плановой задачи по обновлению статусов просроченных заявок.");

        Flux<Request> activeRequests = template.select(query(where("Status").is("In work")), Request.class);

        Mono<Map<Integer, UrgencyCategory>> urgencyMapMono = urgencyCategoryRepository.findAll().collectMap(UrgencyCategory::getUrgencyID);
        Mono<Map<Integer, RequestCustomDay>> customDaysMapMono = customDayRepository.findAll().collectMap(RequestCustomDay::getRequestID);

        Mono.zip(urgencyMapMono, customDaysMapMono)
                .flatMapMany(tuple -> {
                    Map<Integer, UrgencyCategory> urgencyMap = tuple.getT1();
                    Map<Integer, RequestCustomDay> customDaysMap = tuple.getT2();

                    return activeRequests.filter(request -> {
                        UrgencyCategory urgency = urgencyMap.get(request.getUrgencyID());
                        if (urgency == null) return false;

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDaysMap.getOrDefault(request.getRequestID(), new RequestCustomDay()).getDays()
                                : urgency.getDefaultDays();

                        if (daysForTask == null) return false;

                        LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                        boolean isNowOverdue = LocalDateTime.now().isAfter(deadline);

                        return isNowOverdue && (request.getIsOverdue() == null || !request.getIsOverdue());
                    }).map(request -> {
                        request.setIsOverdue(true);
                        return request;
                    });
                })
                .collectList()
                .flatMapMany(requestRepository::saveAll)
                .count()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("Успешно обновлено {} заявок как просроченные.", count);
                            } else {
                                log.info("Новых просроченных заявок не найдено.");
                            }
                        },
                        error -> log.error("Ошибка во время планового обновления статусов просроченных заявок.", error)
                );
    }
}