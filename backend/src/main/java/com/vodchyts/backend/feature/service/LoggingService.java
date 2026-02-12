package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.ApplicationLog;
import com.vodchyts.backend.feature.repository.ReactiveApplicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LoggingService {

    private static final Logger log = LoggerFactory.getLogger(LoggingService.class);
    private final ReactiveApplicationLogRepository logRepository;

    public LoggingService(ReactiveApplicationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public Mono<Void> log(String level, String loggerName, String message, 
                         Throwable exception, Integer userID, String userLogin,
                         String ipAddress, String userAgent, String endpoint, 
                         String requestMethod, String requestID) {
        ApplicationLog logEntry = new ApplicationLog();
        logEntry.setLogLevel(level);
        logEntry.setLoggerName(loggerName);
        logEntry.setMessage(message);
        logEntry.setUserID(userID);
        logEntry.setUserLogin(userLogin);
        logEntry.setIPAddress(ipAddress);
        logEntry.setUserAgent(userAgent);
        logEntry.setEndpoint(endpoint);
        logEntry.setRequestMethod(requestMethod);
        logEntry.setRequestID(requestID);
        logEntry.setLogDate(LocalDateTime.now());

        if (exception != null) {
            logEntry.setExceptionMessage(exception.getMessage());
            if (exception.getStackTrace() != null && exception.getStackTrace().length > 0) {
                StringBuilder stackTrace = new StringBuilder();
                for (StackTraceElement element : exception.getStackTrace()) {
                    stackTrace.append(element.toString()).append("\n");
                }
                logEntry.setStackTrace(stackTrace.toString());
            }
        }

        return logRepository.save(logEntry)
                .doOnError(error -> log.error("Failed to save log entry", error))
                .then();
    }

    public Mono<Void> logInfo(String loggerName, String message, Integer userID, 
                              String userLogin, String ipAddress, String userAgent, 
                              String endpoint, String requestMethod) {
        return log("INFO", loggerName, message, null, userID, userLogin, 
                  ipAddress, userAgent, endpoint, requestMethod, generateRequestID());
    }

    public Mono<Void> logWarn(String loggerName, String message, Integer userID, 
                             String userLogin, String ipAddress, String userAgent, 
                             String endpoint, String requestMethod) {
        return log("WARN", loggerName, message, null, userID, userLogin, 
                  ipAddress, userAgent, endpoint, requestMethod, generateRequestID());
    }

    public Mono<Void> logError(String loggerName, String message, Throwable exception, 
                               Integer userID, String userLogin, String ipAddress, 
                               String userAgent, String endpoint, String requestMethod) {
        return log("ERROR", loggerName, message, exception, userID, userLogin, 
                  ipAddress, userAgent, endpoint, requestMethod, generateRequestID());
    }

    public Mono<Void> logDebug(String loggerName, String message, Integer userID, 
                               String userLogin, String ipAddress, String userAgent, 
                               String endpoint, String requestMethod) {
        return log("DEBUG", loggerName, message, null, userID, userLogin, 
                  ipAddress, userAgent, endpoint, requestMethod, generateRequestID());
    }

    public Mono<Long> deleteOldLogs(LocalDateTime beforeDate) {
        return logRepository.deleteByLogDateBefore(beforeDate)
                .map(Integer::longValue);
    }

    private String generateRequestID() {
        return UUID.randomUUID().toString();
    }
}

