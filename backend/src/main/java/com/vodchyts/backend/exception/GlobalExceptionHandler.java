package com.vodchyts.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.ofEntries(
            Map.entry("UQ_Users_Login", "Пользователь с таким логином уже существует."),
            Map.entry("UQ_Shops_ShopName", "Магазин с таким названием уже существует."),
            Map.entry("UQ_ShopContractorChats_TelegramID", "Чат с таким Telegram ID уже используется в системе."),
            Map.entry("UQ_ShopContractorChats_Shop_User", "Связь для этой пары магазина и подрядчика уже существует."),
            Map.entry("UQ_MessageTemplates_Title", "Шаблон с таким заголовком уже существует."),
            Map.entry("UQ_Notifications_Title", "Уведомление с таким названием уже существует."),

            // Ошибки удаления (Foreign Keys)
            Map.entry("FK_Shops_Users", "Нельзя удалить пользователя: он назначен менеджером магазина."),
            Map.entry("FK_ShopContractorChats_Users", "Нельзя удалить пользователя: он назначен подрядчиком в настройках чатов."),
            Map.entry("FK_Requests_Shops", "Нельзя удалить магазин: в нем есть созданные заявки."),
            Map.entry("FK_Requests_AssignedContractor", "Нельзя удалить пользователя: на него назначены активные заявки."),
            Map.entry("FK_Requests_WorkCategories", "Нельзя удалить категорию: она используется в заявках."),
            Map.entry("FK_RequestComments_Users", "Нельзя удалить пользователя: он оставлял комментарии к заявкам."),
            Map.entry("FK_Requests_UrgencyCategories", "Нельзя удалить категорию срочности: она используется в заявках.")
    );

    private String findPrettyMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                for (Map.Entry<String, String> entry : CONSTRAINT_MESSAGES.entrySet()) {
                    if (msg.contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
            }
            cause = cause.getCause();
        }
        return null;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String prettyMsg = findPrettyMessage(ex);
        String finalMsg = (prettyMsg != null) ? prettyMsg : "Ошибка базы данных: нарушение целостности данных.";

        log.warn("Database Constraint Violated: {}", ex.getMostSpecificCause().getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(finalMsg));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<String>> handleRuntimeException(RuntimeException ex) {
        // Сначала проверяем, нет ли внутри RuntimeException ошибки базы данных
        String prettyMsg = findPrettyMessage(ex);
        if (prettyMsg != null) {
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(prettyMsg));
        }

        log.error("Runtime Error: ", ex);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<String>> handleUserNotFound(UserNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()));
    }

    @ExceptionHandler(OperationNotAllowedException.class)
    public Mono<ResponseEntity<String>> handleOperationNotAllowed(OperationNotAllowedException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public Mono<ResponseEntity<String>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Нет доступа к этому ресурсу."));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<String>> handleValidationExceptions(WebExchangeBindException ex) {
        String errors = ex.getBindingResult()
                .getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<String>> handleGenericException(Exception ex) {
        log.error("Critical error: ", ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Внутренняя ошибка сервера."));
    }
}