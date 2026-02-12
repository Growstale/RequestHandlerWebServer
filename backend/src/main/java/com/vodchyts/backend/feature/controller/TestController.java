package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.service.RequestUpdateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@PreAuthorize("hasRole('RetailAdmin')")
public class TestController {

    private final RequestUpdateService requestUpdateService;

    public TestController(RequestUpdateService requestUpdateService) {
        this.requestUpdateService = requestUpdateService;
    }

    @GetMapping("/config")
    public Mono<Map<String, Object>> getConfig() {
        return Mono.just(requestUpdateService.getCurrentConfig());
    }

    @PostMapping("/interval")
    public Mono<Void> setCheckInterval(@RequestBody Map<String, Long> payload) {
        return Mono.fromRunnable(() -> {
            Long interval = payload.get("interval");
            if (interval != null && interval >= 1000) {
                requestUpdateService.restartOverdueCheck(interval);
            }
        });
    }

    @PostMapping("/cron")
    public Mono<Void> setReminderCron(@RequestBody Map<String, String> payload) {
        return Mono.fromRunnable(() -> {
            String cron = payload.get("cron");
            if (cron != null && !cron.isBlank()) {
                requestUpdateService.restartDailyReminder(cron);
            }
        });
    }

    @PostMapping("/requests/{requestId}/date")
    public Mono<Void> updateRequestDate(@PathVariable Integer requestId, @RequestBody Map<String, String> payload) {
        return Mono.fromRunnable(() -> {
            String dateStr = payload.get("date");
            LocalDateTime newDate = LocalDateTime.parse(dateStr);

            if (newDate.isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Нельзя устанавливать дату в будущем!");
            }

            requestUpdateService.updateRequestDate(requestId, newDate).subscribe();
        });
    }
    @PostMapping("/trigger-check")
    public Mono<Long> triggerCheckNow() {
        return requestUpdateService.forceCheckNow();
    }

    @PostMapping("/trigger-remind")
    public Mono<Void> triggerRemindNow() {
        return Mono.fromRunnable(requestUpdateService::forceRemindNow);
    }
}