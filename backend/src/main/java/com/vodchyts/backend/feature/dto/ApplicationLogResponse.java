package com.vodchyts.backend.feature.dto;

import java.time.LocalDateTime;

public record ApplicationLogResponse(
        Long logID,
        String logLevel,
        String loggerName,
        String message,
        String exceptionMessage,
        String stackTrace,
        Integer userID,
        String userLogin,
        String ipAddress,
        String userAgent,
        String endpoint,
        String requestMethod,
        String requestID,
        LocalDateTime logDate
) {
}

