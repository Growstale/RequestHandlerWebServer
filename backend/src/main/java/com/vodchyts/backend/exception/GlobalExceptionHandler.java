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

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<String>> handleOther(RuntimeException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()));
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

    @ExceptionHandler(ShopAlreadyExistsException.class)
    public Mono<ResponseEntity<String>> handleShopAlreadyExists(ShopAlreadyExistsException ex) {
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

}
