package com.vodchyts.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<String>> handleRuntimeException(RuntimeException ex) {
        return Mono.just(
                ResponseEntity
                        .badRequest()
                        .body(ex.getMessage())
        );
    }
}