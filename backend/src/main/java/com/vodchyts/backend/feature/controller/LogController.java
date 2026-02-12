package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.ApplicationLogResponse;
import com.vodchyts.backend.feature.dto.PagedLogResponse;
import com.vodchyts.backend.feature.entity.ApplicationLog;
import com.vodchyts.backend.feature.repository.ReactiveApplicationLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/logs")
@PreAuthorize("hasRole('RetailAdmin')")
public class LogController {

    private final ReactiveApplicationLogRepository logRepository;

    public LogController(ReactiveApplicationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping
    public Mono<ResponseEntity<PagedLogResponse<ApplicationLogResponse>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) Integer userID,
            @RequestParam(required = false) String loggerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Pageable pageable = PageRequest.of(page, size);
        
        Mono<List<ApplicationLog>> logsMono;
        Mono<Long> countMono;

        if (logLevel != null && !logLevel.isEmpty()) {
            logsMono = logRepository.findByLogLevelOrderByLogDateDesc(logLevel, pageable)
                    .collectList();
            countMono = logRepository.countByLogLevelAndLogDateBetween(
                    logLevel,
                    startDate != null ? startDate : LocalDateTime.of(2000, 1, 1, 0, 0),
                    endDate != null ? endDate : LocalDateTime.now()
            );
        } else if (userID != null) {
            logsMono = logRepository.findByUserIDOrderByLogDateDesc(userID, pageable)
                    .collectList();
            countMono = logRepository.count();
        } else if (loggerName != null && !loggerName.isEmpty()) {
            logsMono = logRepository.findByLoggerNameContainingIgnoreCaseOrderByLogDateDesc(loggerName, pageable)
                    .collectList();
            countMono = logRepository.count();
        } else if (startDate != null || endDate != null) {
            LocalDateTime start = startDate != null ? startDate : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
            logsMono = logRepository.findByLogDateBetweenOrderByLogDateDesc(start, end, pageable)
                    .collectList();
            countMono = logRepository.count();
        } else {
            logsMono = logRepository.findAllByOrderByLogDateDesc(pageable)
                    .collectList();
            countMono = logRepository.count();
        }

        return Mono.zip(logsMono, countMono)
                .map(tuple -> {
                    List<ApplicationLog> logs = tuple.getT1();
                    long total = tuple.getT2();
                    List<ApplicationLogResponse> responses = logs.stream()
                            .map(log -> new ApplicationLogResponse(
                                    log.getLogID(),
                                    log.getLogLevel(),
                                    log.getLoggerName(),
                                    log.getMessage(),
                                    log.getExceptionMessage(),
                                    log.getStackTrace(),
                                    log.getUserID(),
                                    log.getUserLogin(),
                                    log.getIPAddress(),
                                    log.getUserAgent(),
                                    log.getEndpoint(),
                                    log.getRequestMethod(),
                                    log.getRequestID(),
                                    log.getLogDate()
                            ))
                            .toList();
                    
                    int totalPages = (int) Math.ceil((double) total / size);
                    return ResponseEntity.ok(new PagedLogResponse<>(
                            responses,
                            total,
                            totalPages,
                            page,
                            size
                    ));
                });
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('RetailAdmin')")
    public Mono<ResponseEntity<Object>> getLogStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        LocalDateTime start = startDate != null ? startDate : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();

        Mono<Long> errorCount = logRepository.countByLogLevelAndLogDateBetween("ERROR", start, end);
        Mono<Long> warnCount = logRepository.countByLogLevelAndLogDateBetween("WARN", start, end);
        Mono<Long> infoCount = logRepository.countByLogLevelAndLogDateBetween("INFO", start, end);

        return Mono.zip(errorCount, warnCount, infoCount)
                .map(tuple -> {
                    return ResponseEntity.ok(Map.of(
                            "errors", tuple.getT1(),
                            "warnings", tuple.getT2(),
                            "info", tuple.getT3(),
                            "startDate", start,
                            "endDate", end
                    ));
                });
    }
}

