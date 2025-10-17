package com.vodchyts.backend.exception;

public class WorkCategoryAlreadyExistsException extends RuntimeException {
    public WorkCategoryAlreadyExistsException(String message) {
        super(message);
    }
}