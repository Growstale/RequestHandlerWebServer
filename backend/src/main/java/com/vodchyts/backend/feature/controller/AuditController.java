package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.AuditLogResponse;
import com.vodchyts.backend.feature.dto.PagedLogResponse;
import com.vodchyts.backend.feature.entity.AuditLog;
import com.vodchyts.backend.feature.repository.ReactiveAuditLogRepository;
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
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('RetailAdmin')")
public class AuditController {

    private final ReactiveAuditLogRepository auditRepository;

    public AuditController(ReactiveAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    public Mono<ResponseEntity<PagedLogResponse<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Integer userID,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        Pageable pageable = PageRequest.of(page, size);
        
        Mono<List<AuditLog>> logsMono;
        Mono<Long> countMono;

        if (userID != null && startDate != null && endDate != null) {
            logsMono = auditRepository.findByUserIDAndLogDateBetweenOrderByLogDateDesc(
                    userID, startDate, endDate, pageable
            ).collectList();
            countMono = auditRepository.count();
        } else if (userID != null) {
            logsMono = auditRepository.findByUserIDOrderByLogDateDesc(userID, pageable)
                    .collectList();
            countMono = auditRepository.count();
        } else if (tableName != null && !tableName.isEmpty()) {
            logsMono = auditRepository.findByTableNameOrderByLogDateDesc(tableName, pageable)
                    .collectList();
            countMono = auditRepository.count();
        } else if (action != null && !action.isEmpty()) {
            logsMono = auditRepository.findByActionOrderByLogDateDesc(action, pageable)
                    .collectList();
            countMono = auditRepository.count();
        } else if (startDate != null || endDate != null) {
            LocalDateTime start = startDate != null ? startDate : LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
            logsMono = auditRepository.findByLogDateBetweenOrderByLogDateDesc(start, end, pageable)
                    .collectList();
            countMono = auditRepository.count();
        } else {
            logsMono = auditRepository.findAllByOrderByLogDateDesc(pageable)
                    .collectList();
            countMono = auditRepository.count();
        }

        return Mono.zip(logsMono, countMono)
                .map(tuple -> {
                    List<AuditLog> logs = tuple.getT1();
                    long total = tuple.getT2();
                    List<AuditLogResponse> responses = logs.stream()
                            .map(log -> new AuditLogResponse(
                                    log.getLogID(),
                                    log.getTableName(),
                                    log.getAction(),
                                    log.getRecordID(),
                                    log.getUserID(),
                                    log.getUserLogin(),
                                    log.getLogDate(),
                                    log.getChanges(),
                                    log.getIPAddress(),
                                    log.getUserAgent(),
                                    log.getEndpoint(),
                                    log.getRequestMethod()
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
    public Mono<ResponseEntity<Map<String, Object>>> getAuditStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        LocalDateTime start = startDate != null ? startDate : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();

        Mono<Long> createCount = auditRepository.findByActionOrderByLogDateDesc("CREATE", PageRequest.of(0, 1))
                .count();
        Mono<Long> updateCount = auditRepository.findByActionOrderByLogDateDesc("UPDATE", PageRequest.of(0, 1))
                .count();
        Mono<Long> deleteCount = auditRepository.findByActionOrderByLogDateDesc("DELETE", PageRequest.of(0, 1))
                .count();

        return Mono.zip(createCount, updateCount, deleteCount)
                .map(tuple -> {
                    return ResponseEntity.ok(Map.of(
                            "creates", tuple.getT1(),
                            "updates", tuple.getT2(),
                            "deletes", tuple.getT3(),
                            "startDate", start,
                            "endDate", end
                    ));
                });
    }
}

