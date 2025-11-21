package com.vodchyts.backend.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String message) {}

    @ExceptionHandler(UserNotFoundException.class)
    public Mono<ResponseEntity<String>> handleUserNotFound(UserNotFoundException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public Mono<ResponseEntity<String>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public Mono<ResponseEntity<String>> handleInvalidToken(InvalidTokenException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<String>> handleUnauthorized(UnauthorizedException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public Mono<ResponseEntity<String>> handleInvalidPassword(InvalidPasswordException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()));
    }

    @ExceptionHandler(OperationNotAllowedException.class)
    public Mono<ResponseEntity<String>> handleOperationNotAllowed(OperationNotAllowedException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        // Обрабатываем ошибки нарушения уникального ключа для чатов
        if (message != null) {
            if (message.contains("UQ_ShopContractorChats_TelegramID")) {
                return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("Чат с таким Telegram ID уже существует."));
            }
            if (message.contains("UQ_ShopContractorChats_Shop_User")) {
                // Определяем, есть ли подрядчик в сообщении об ошибке
                if (message.contains("<NULL>")) {
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("Связь для этого магазина без подрядчика уже существует."));
                } else {
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("Связь для этой пары магазина и подрядчика уже существует."));
                }
            }
        }
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message != null ? message : "Произошла ошибка."));
    }

    @ExceptionHandler(ShopAlreadyExistsException.class)
    public Mono<ResponseEntity<String>> handleShopAlreadyExists(ShopAlreadyExistsException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage()));
    }

    @ExceptionHandler(WorkCategoryAlreadyExistsException.class)
    public Mono<ResponseEntity<String>> handleWorkCategoryAlreadyExists(WorkCategoryAlreadyExistsException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage()));
    }

    @ExceptionHandler(NotificationAlreadyExistsException.class)
    public Mono<ResponseEntity<String>> handleNotificationAlreadyExists(NotificationAlreadyExistsException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage()));
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
    public Mono<ResponseEntity<Object>> handleGenericException(Exception ex) {
        var errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Произошла внутренняя ошибка сервера."
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }
}
