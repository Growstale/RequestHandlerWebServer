package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Integer logID,
        String tableName,
        String action,
        Integer recordID,
        Integer userID,
        String userLogin,
        LocalDateTime logDate,
        String changes,
        String ipAddress,
        String userAgent,
        String endpoint,
        String requestMethod
) {
}

