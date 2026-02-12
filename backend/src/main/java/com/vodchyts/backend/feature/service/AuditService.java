package com.vodchyts.backend.feature.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodchyts.backend.feature.entity.AuditLog;
import com.vodchyts.backend.feature.repository.ReactiveAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final ReactiveAuditLogRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(ReactiveAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
        this.objectMapper = new ObjectMapper();
    }

    public Mono<Void> audit(String action, String tableName, Integer recordID, 
                           Object oldValue, Object newValue, Integer userID, 
                           String userLogin, String ipAddress, String userAgent, 
                           String endpoint, String requestMethod) {
        AuditLog auditEntry = new AuditLog();
        auditEntry.setAction(action);
        auditEntry.setTableName(tableName);
        auditEntry.setRecordID(recordID);
        auditEntry.setUserID(userID);
        auditEntry.setUserLogin(userLogin);
        auditEntry.setIPAddress(ipAddress);
        auditEntry.setUserAgent(userAgent);
        auditEntry.setEndpoint(endpoint);
        auditEntry.setRequestMethod(requestMethod);
        auditEntry.setLogDate(LocalDateTime.now());

        try {
            Map<String, Object> changes = Map.of(
                    "oldValue", oldValue != null ? oldValue : "null",
                    "newValue", newValue != null ? newValue : "null"
            );
            auditEntry.setChanges(objectMapper.writeValueAsString(changes));
        } catch (Exception e) {
            log.warn("Failed to serialize audit changes", e);
            auditEntry.setChanges("{\"error\":\"Failed to serialize changes\"}");
        }

        return auditRepository.save(auditEntry)
                .doOnError(error -> log.error("Failed to save audit entry", error))
                .then();
    }

    public Mono<Void> auditCreate(String tableName, Integer recordID, Object newValue, 
                                  Integer userID, String userLogin, String ipAddress, 
                                  String userAgent, String endpoint, String requestMethod) {
        return audit("CREATE", tableName, recordID, null, newValue, userID, userLogin, 
                    ipAddress, userAgent, endpoint, requestMethod);
    }

    public Mono<Void> auditUpdate(String tableName, Integer recordID, Object oldValue, 
                                  Object newValue, Integer userID, String userLogin, 
                                  String ipAddress, String userAgent, String endpoint, 
                                  String requestMethod) {
        return audit("UPDATE", tableName, recordID, oldValue, newValue, userID, userLogin, 
                    ipAddress, userAgent, endpoint, requestMethod);
    }

    public Mono<Void> auditDelete(String tableName, Integer recordID, Object oldValue, 
                                  Integer userID, String userLogin, String ipAddress, 
                                  String userAgent, String endpoint, String requestMethod) {
        return audit("DELETE", tableName, recordID, oldValue, null, userID, userLogin, 
                    ipAddress, userAgent, endpoint, requestMethod);
    }

    public Mono<Long> deleteOldAuditLogs(LocalDateTime beforeDate) {
        return auditRepository.deleteByLogDateBefore(beforeDate)
                .map(Integer::longValue);
    }
}

