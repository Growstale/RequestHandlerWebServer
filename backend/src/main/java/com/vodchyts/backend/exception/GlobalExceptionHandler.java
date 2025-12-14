package com.vodchyts.backend.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
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

    private String getReadableErrorMessage(String msg) {
        if (msg == null) return "Произошла ошибка.";

        // --- УДАЛЕНИЕ ПОЛЬЗОВАТЕЛЯ (User) ---

        // 1. Пользователь привязан к чатам (Конфликт в ShopContractorChats, колонка ContractorID)
        if (msg.contains("ShopContractorChats") && (msg.contains("ContractorID") || msg.contains("FK_ShopContractorChats_Users"))) {
            return "Нельзя удалить пользователя: он назначен подрядчиком в настройках чатов.";
        }

        // 2. Пользователь - менеджер магазина (Конфликт в Shops, колонка UserID)
        // ВАЖНО: Проверяем наличие 'UserID', чтобы не путать с удалением самого магазина
        if (msg.contains("Shops") && (msg.contains("UserID") || msg.contains("FK__Shops__UserID"))) {
            return "Нельзя удалить пользователя: он назначен менеджером магазина. Снимите его с должности в разделе 'Магазины'.";
        }

        // 3. Пользователь - исполнитель заявок (Конфликт в Requests, колонка AssignedContractorID)
        if (msg.contains("Requests") && (msg.contains("AssignedContractorID") || msg.contains("Assign"))) {
            return "Нельзя удалить пользователя: на него назначены заявки. Переназначьте или удалите заявки.";
        }

        // 4. Пользователь оставлял комментарии
        if (msg.contains("RequestComments")) {
            return "Нельзя удалить пользователя: он оставлял комментарии к заявкам.";
        }


        // --- УДАЛЕНИЕ МАГАЗИНА (Shop) ---

        // 5. Магазин привязан к чатам (Конфликт в ShopContractorChats, колонка ShopID)
        if (msg.contains("ShopContractorChats") && msg.contains("ShopID")) {
            return "Нельзя удалить магазин: для него настроены чаты. Удалите связи в разделе 'Управление чатами'.";
        }

        // 6. Магазин привязан к заявкам (Конфликт в Requests, колонка ShopID)
        if (msg.contains("Requests") && msg.contains("ShopID")) {
            return "Нельзя удалить магазин: к нему привязаны заявки. Удалите заявки этого магазина перед его удалением.";
        }


        // --- ПРОЧИЕ ОШИБКИ (Уникальность и т.д.) ---

        if (msg.contains("UQ_ShopContractorChats_TelegramID")) {
            return "Чат с таким Telegram ID уже существует.";
        }
        if (msg.contains("UQ_ShopContractorChats_Shop_User")) {
            if (msg.contains("<NULL>")) {
                return "Связь для этого магазина без подрядчика уже существует.";
            } else {
                return "Связь для этой пары магазина и подрядчика уже существует.";
            }
        }

        if (msg.contains("REFERENCE constraint")) {
            return "Невозможно выполнить операцию: запись используется в других таблицах.";
        }

        return msg;
    }


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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Пытаемся достать самое глубокое сообщение
        String msg = ex.getMostSpecificCause().getMessage();
        String userMessage = getReadableErrorMessage(msg);

        // Если сообщение изменилось (мы его распознали), возвращаем 409 Conflict
        // Если нет - тоже 409, так как это ошибка целостности данных
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(userMessage));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<String>> handleRuntimeException(RuntimeException ex) {
        // Важно: иногда R2DBC кидает RuntimeException, внутри которого лежит SQL ошибка
        String msg = ex.getMessage();

        // Пытаемся найти причину глубже, если она есть
        if (ex.getCause() != null) {
            msg = ex.getCause().getMessage();
        }

        String userMessage = getReadableErrorMessage(msg);

        // Если мы распознали ошибку как конфликт данных (текст изменился), вернем 409
        // Иначе вернем 400 Bad Request
        HttpStatus status = userMessage.equals(msg) ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;

        return Mono.just(ResponseEntity.status(status).body(userMessage));
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
        ex.printStackTrace(); // Полезно для отладки в консоли
        var errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Произошла внутренняя ошибка сервера."
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }
}